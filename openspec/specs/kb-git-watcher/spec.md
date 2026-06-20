# kb-git-watcher Spec

## Purpose

管理知识库 Git 仓库的配置、克隆、定时同步与 Markdown 变更事件发布。

## Requirements

### Requirement: KbRepository 聚合根
系统 SHALL 维护 `KbRepository` 聚合根，包含以下字段：`id`（Long，自增主键）、`name`（仓库展示名，VARCHAR(100)，NOT NULL，唯一）、`remoteUrl`（Git 远端 URL，VARCHAR(500)，NOT NULL）、`branch`（监听分支，VARCHAR(100)，默认 `main`）、`localPath`（本地 clone 绝对路径，VARCHAR(500)，NOT NULL）、`authType`（凭据类型，枚举 `NONE/HTTPS_TOKEN/SSH_KEY_PATH`，NOT NULL，默认 `NONE`）、`authSecret`（凭据内容，VARCHAR(1000)，nullable，HTTPS token 或 SSH 私钥路径）、`pollIntervalMs`（轮询间隔毫秒，BIGINT，默认 3600000）、`syncStatus`（枚举 `HEALTHY/SYNCING/ERROR`，NOT NULL，默认 `HEALTHY`）、`lastSyncedCommit`（最后一次同步的 commit hash，VARCHAR(40)，nullable）、`lastSyncedAt`（最后一次同步时间，DATETIME，nullable）、`lastErrorMessage`（最后一次错误信息，VARCHAR(1000)，nullable）、`version`（乐观锁，从 1 开始）、`deleted`（逻辑删除标记，默认 0）、`createdAt`、`updatedAt`。

聚合根 SHALL 为纯 Java 对象（无 MyBatis 注解），通过静态工厂方法创建，内部封装同步状态转移与凭据 mask 行为。

#### Scenario: 创建仓库字段完整性
- **WHEN** ADMIN 提供 name、remoteUrl、branch、authType、authSecret、pollIntervalMs 创建仓库
- **THEN** `syncStatus` MUST 为 `HEALTHY`，`lastSyncedCommit` MUST 为 null，`version` MUST 为 1，`deleted` MUST 为 0

#### Scenario: 重建仓库
- **WHEN** 从持久化层加载仓库数据
- **THEN** 系统 MUST 通过 `reconstruct` 静态方法还原所有字段，包括 id、时间戳、version、syncStatus、lastSyncedCommit

---

### Requirement: 单仓库约束
系统 SHALL 至多维护 1 个未删除的 `KbRepository`。已存在未删除仓库时再次创建 SHALL 抛出 `KB_REPO_ALREADY_CONFIGURED` 错误。

#### Scenario: 重复创建被拒绝
- **WHEN** ADMIN 已配置一个仓库后再次发送 `POST /api/v1/kb/repositories`
- **THEN** 系统 MUST 返回 `KB_REPO_ALREADY_CONFIGURED` 错误

#### Scenario: 删除后允许重新创建
- **WHEN** ADMIN 删除现有仓库后再次创建
- **THEN** 创建 MUST 成功

---

### Requirement: 同步状态机
系统 SHALL 在每次同步任务前后维护 `syncStatus` 状态机：`HEALTHY → SYNCING → HEALTHY/ERROR → SYNCING`。状态为 `SYNCING` 时定时任务 SHALL 跳过本轮，防止并发同步。

`ERROR` 状态 SHALL 不阻塞下一轮同步——下次定时触发时系统重新尝试，并在 `lastErrorMessage` 写入最新错误内容。

#### Scenario: 正常同步完成回到 HEALTHY
- **WHEN** 一次定时同步任务执行成功（无论是否检测到变更）
- **THEN** `syncStatus` MUST 为 `HEALTHY`，`lastSyncedCommit` MUST 更新为最新 commit，`lastErrorMessage` MUST 为 null

#### Scenario: 同步失败置 ERROR
- **WHEN** 一次同步任务因 git pull/clone/auth 异常失败
- **THEN** `syncStatus` MUST 为 `ERROR`，`lastErrorMessage` MUST 含异常摘要，`lastSyncedCommit` MUST 保持上一次成功的值

#### Scenario: SYNCING 时跳过新一轮调度
- **WHEN** 定时任务触发时仓库 `syncStatus` 已是 `SYNCING`
- **THEN** 系统 MUST 跳过本轮，记录 DEBUG 日志，不抛出异常

---

### Requirement: Git 仓库克隆与拉取
系统 SHALL 使用 JGit 维护本地工作区。首次同步（`lastSyncedCommit` 为 null 或本地目录不存在）SHALL 执行 `clone`；后续同步 SHALL 执行 `fetch` + `reset --hard origin/<branch>`，确保本地工作区与远端分支完全一致，不保留任何本地修改。

凭据 SHALL 根据 `authType` 注入：`HTTPS_TOKEN` 用 `UsernamePasswordCredentialsProvider("token", token)`；`SSH_KEY_PATH` 用 `SshSessionFactory` 指向私钥绝对路径；`NONE` 不注入凭据。

#### Scenario: 首次同步触发 clone
- **WHEN** 同步任务执行时 `lastSyncedCommit` 为 null 或 `localPath` 目录不存在
- **THEN** 系统 MUST 执行 `git clone <remoteUrl> --branch <branch> <localPath>`

#### Scenario: 后续同步触发 fetch + reset
- **WHEN** 同步任务执行时本地工作区已存在
- **THEN** 系统 MUST 先 `fetch origin`，再 `reset --hard origin/<branch>`

#### Scenario: 凭据失败抛 KB_GIT_AUTH_FAILED
- **WHEN** Git 操作因 `TransportException`（凭据无效/拒绝）失败
- **THEN** 系统 MUST 抛 `KB_GIT_AUTH_FAILED` 错误，状态置 `ERROR`

#### Scenario: clone 失败抛 KB_GIT_CLONE_FAILED
- **WHEN** 首次 clone 因网络/磁盘/路径异常失败
- **THEN** 系统 MUST 抛 `KB_GIT_CLONE_FAILED`，状态置 `ERROR`

#### Scenario: pull 失败抛 KB_GIT_PULL_FAILED
- **WHEN** 后续 fetch/reset 因网络/分支不存在等异常失败
- **THEN** 系统 MUST 抛 `KB_GIT_PULL_FAILED`，状态置 `ERROR`

---

### Requirement: Markdown 变更检测与事件发布
系统 SHALL 在每次同步成功后对比 `lastSyncedCommit`（旧）与当前 `HEAD`（新）两个 tree，提取所有 `*.md` 文件的变更，按 `ADDED / MODIFIED / DELETED` 分类发布 `KbDocumentChangedEvent` 领域事件。

事件 SHALL 包含以下字段：`repositoryId`（Long）、`path`（相对仓库根的文件路径）、`changeType`（枚举 `ADDED/MODIFIED/DELETED`）、`commitHash`（新 HEAD 的 commit hash）。

`RENAMED` 类型的 DiffEntry SHALL 被拆分为一条 `DELETED`（旧路径）+ 一条 `ADDED`（新路径），让下游索引器以统一方式处理。

非 `*.md` 文件的变更 SHALL 被忽略，不发布事件。

首次 clone（`lastSyncedCommit` 为 null）SHALL 把当前 HEAD 树下所有 `*.md` 文件作为 `ADDED` 事件发布。

#### Scenario: 新增 markdown 文件触发 ADDED 事件
- **WHEN** 远端新提交一个新 markdown 文件
- **THEN** 系统 MUST 发布一条 `KbDocumentChangedEvent`，`changeType=ADDED`，`path` 为该文件相对路径

#### Scenario: 修改 markdown 触发 MODIFIED 事件
- **WHEN** 远端修改了一个已存在的 markdown 文件
- **THEN** 系统 MUST 发布 `MODIFIED` 事件

#### Scenario: 删除 markdown 触发 DELETED 事件
- **WHEN** 远端删除了一个 markdown 文件
- **THEN** 系统 MUST 发布 `DELETED` 事件

#### Scenario: 重命名拆分为 DELETED + ADDED
- **WHEN** 远端把 `a.md` 重命名为 `b.md`
- **THEN** 系统 MUST 发布两条事件：`DELETED a.md` 与 `ADDED b.md`

#### Scenario: 非 markdown 文件被忽略
- **WHEN** 远端新增或修改了 `.png` / `.txt` / `.json` 等非 `.md` 文件
- **THEN** 系统 MUST 不发布任何 `KbDocumentChangedEvent`

#### Scenario: 首次 clone 全量发布 ADDED
- **WHEN** 仓库首次同步成功（`lastSyncedCommit` 之前为 null）
- **THEN** 系统 MUST 为当前 HEAD 下所有 `*.md` 文件各发一条 `ADDED` 事件

---

### Requirement: 定时同步调度
系统 SHALL 通过 Spring `@Scheduled(fixedDelay)` 按 `pollIntervalMs` 间隔执行同步任务。`fixedDelay` 从应用配置 `kb.git.poll-interval-ms` 读取（默认 3600000，1 小时），运行时不动态读取数据库字段（仓库表里的 `pollIntervalMs` 仅作展示与未来扩展用）。

调度任务 SHALL 单线程串行执行，任意时刻最多一个同步在跑。

#### Scenario: 启动后按间隔执行
- **WHEN** 应用启动且配置了仓库
- **THEN** 调度器 MUST 按 `kb.git.poll-interval-ms` 间隔触发同步任务

#### Scenario: 无仓库配置时调度任务空跑
- **WHEN** 调度任务触发但系统中无未删除仓库
- **THEN** 任务 MUST 安静返回，不抛异常、不打 WARN

---

### Requirement: 仓库管理 REST API
系统 SHALL 提供 `/api/v1/kb/repositories` 资源接口支持仓库的 CRUD 与立即同步。

写操作（创建、更新、删除、立即同步）SHALL 仅限 ADMIN 角色。读操作（列表、详情）SHALL 对所有已登录用户开放，但响应中 `authSecret` 字段 SHALL 对非 ADMIN 用户做 mask（返回 `"***"` 或 null）。

#### Scenario: ADMIN 创建仓库
- **WHEN** ADMIN 发送 `POST /api/v1/kb/repositories`，body 含 name、remoteUrl、branch、authType、authSecret、pollIntervalMs
- **THEN** 系统 MUST 创建仓库并触发首次异步 clone（不阻塞响应），返回创建后的仓库摘要

#### Scenario: ADMIN 更新仓库
- **WHEN** ADMIN 发送 `PUT /api/v1/kb/repositories/{id}`，body 含可变字段 + version
- **THEN** 系统 MUST 执行乐观锁更新；如果 remoteUrl/branch 变更，下次同步走 clone（清理旧本地目录）

#### Scenario: ADMIN 删除仓库
- **WHEN** ADMIN 发送 `DELETE /api/v1/kb/repositories/{id}`
- **THEN** 系统 MUST 逻辑删除仓库记录，同步删除 `localPath` 目录

#### Scenario: ADMIN 触发立即同步
- **WHEN** ADMIN 发送 `POST /api/v1/kb/repositories/{id}/sync`
- **THEN** 系统 MUST 异步触发同步任务（不阻塞响应），返回 `syncStatus=SYNCING`；若已是 `SYNCING` 则返回当前状态不重复触发

#### Scenario: 普通用户查询凭据被 mask
- **WHEN** SUBMITTER 发送 `GET /api/v1/kb/repositories/{id}`
- **THEN** 响应 MUST 不暴露 `authSecret` 真实值（返回 null 或 `"***"`）

#### Scenario: 非 ADMIN 尝试写操作
- **WHEN** SUBMITTER 或 TEAM_MEMBER 尝试 POST/PUT/DELETE/sync
- **THEN** 系统 MUST 返回 `FORBIDDEN` 错误

---

### Requirement: 凭据日志安全
系统 SHALL 在任何日志输出（INFO/WARN/ERROR）中都不打印 `authSecret` 字段的真实内容。日志中涉及该字段时 MUST 替换为 `***`。

#### Scenario: 同步任务日志不泄漏凭据
- **WHEN** 同步任务执行（成功或失败）
- **THEN** 日志中 MUST 不包含 `authSecret` 真实字符串
