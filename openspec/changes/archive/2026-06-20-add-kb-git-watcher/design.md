## Context

DDD 四层架构与 AI 基础设施都已就绪。本 change 引入 RAG 知识库的"上游接入层"——把 Git 仓库当作可信文档源，提供"什么变了"的事件流给后续索引器消费。

当前知识库相关只在前端原型有静态卡片（"已索引文档 38 / 文档块 312 / 今天 09:15"），后端是空的。本 change 把后端从 0 推到"能拉 git、能检测变更、能发事件、能持久化状态"。

错误码 50000 段已预留 `KB_GIT_REPO_NOT_FOUND(50003)`，本 change 新增 4 个错误码补全 git 场景。

## Goals / Non-Goals

**Goals:**

- 持久化 Git 仓库元数据（URL/分支/凭据/本地路径/轮询间隔）
- 首次启动 clone、后续 fetch + reset（避免本地 merge 冲突）
- 定时轮询任务，每次 pull 后对比上次 commit hash 提取变更
- 通过 Spring `ApplicationEvent` 发布变更事件，作为 #12 的契约入口
- 同步状态可观测（HEALTHY/SYNCING/ERROR + 最后一次 commit hash + 错误信息）
- ADMIN 管理 API：配置仓库 / 触发立即同步 / 查看状态
- 凭据安全：HTTPS token / SSH 私钥路径，不在日志里打印明文

**Non-Goals:**

- Markdown 分块、Embedding、Chroma 写入（#12）
- 实体提取、向量检索、降级提示（#13）
- Webhook 触发同步（本 change 走轮询，简单可靠；webhook 后续可加）
- 多仓库支持（MVP 只支持 1 个仓库，多仓库后续扩展时再做）
- Git LFS / 子模块 / 二进制文件处理（只处理 markdown）
- 文件移动检测（rename 视为 DELETED + ADDED 两条事件，足够触发 #12 重新索引）

## Decisions

### D1: 单仓库模式 — kb_repository 表恒有 0 或 1 行

**选择**：表设计支持多行，但 Application 层强制"系统至多 1 个仓库配置"，第二次创建抛 `KB_REPO_ALREADY_CONFIGURED`。

**理由**：
- MVP 阶段一个企业级知识源就够用，过早支持多仓库会引入"同名文档冲突如何路由"等复杂问题
- 表结构留好 id 主键，未来去掉单例约束即可扩展
- 简化前端 UI：只需要一个"配置仓库"按钮，不用列表

**备选**：直接限制单行（PK=固定 1 + UPSERT）——表达力不足，未来扩展需要改 schema。

### D2: 凭据存储 — 数据库明文 + 仅 ADMIN 可读，本 change 不引入加密

**选择**：
- `auth_type`（NONE / HTTPS_TOKEN / SSH_KEY_PATH）
- `auth_secret`（HTTPS token 字符串 / SSH 私钥**绝对路径**）
- 读操作仅 ADMIN（沿用 `@RequireRole`），普通用户接口不返回该字段
- 凭据在日志里**永远不打印**（Assembler 层用专门的 mask 工具）

**理由**：
- 项目当前没有加密基础设施（KMS / Jasypt 都未引入），本 change 引入会偏离主线
- SSH 不存私钥内容、只存路径，私钥本身放在服务器文件系统并依赖 OS 权限
- HTTPS token 是首选凭据（更易管理 / 旋转 / 撤销），SSH 是兼容选项
- 数据库层访问已经被 DBA 控制；ADMIN 角色对应运维人员，本来就是凭据所有者
- 未来如需加密，加一层 `AttributeConverter`（Jasypt）即可向后兼容

**备选**：Jasypt 加密——更安全但本 change 引入会拖时间，列入后续 Tech Debt。

### D3: Git 操作策略 — fetch + reset --hard origin/branch（不 merge）

**选择**：本地工作区始终被远端覆盖，不保留任何本地修改。

**理由**：
- 知识库不是开发分支，本地不应有任何手工编辑
- `merge` 可能冲突，需人工介入；`reset --hard` 总能成功且确定性强
- 服务重启后状态可复现（last_synced_commit ↔ HEAD 一致）

**备选**：`pull --rebase`——同样会冲突，且对当前场景没收益。

### D4: 变更检测 — JGit DiffCommand(oldTree, newTree)

**选择**：保留上次同步的 commit hash，新 pull 后用 `TreeWalk + DiffEntry` 对比两个 tree，过滤出 `*.md` 文件，按 `ADDED / MODIFIED / DELETED` 分类发事件。

**理由**：
- 不需要遍历整个仓库，对比 tree 是 O(变更) 而非 O(全部)
- DiffEntry 已经把 ChangeType 给到，直接映射
- rename 由 DiffEntry 上报为 `RENAMED`——本 change 简化为 DELETED + ADDED 两条事件，让 #12 不感知 rename 复杂度

### D5: 调度 — Spring @Scheduled 单线程串行 + 数据库状态保护

**选择**：
- 用 `@Scheduled(fixedDelayString = "${kb.git.poll-interval-ms:3600000}")`，默认 1 小时
- 任务开始前 CAS 检查 `sync_status` 必须是 `HEALTHY`，否则跳过本轮
- 任务执行时把状态写为 `SYNCING`，结束置回 `HEALTHY`（或 `ERROR` 含错误消息）
- 单线程：不会出现两个 pull 互相覆盖

**理由**：
- 单仓库 + 单实例部署，串行最简单；分布式锁等后续如有多实例再加
- 状态字段同时供前端展示 + 任务调度自检，一物两用

**备选**：用 Quartz——单仓库定时器 overkill；`@Async` + ReentrantLock——状态需要持久化给前端看，绕不开 DB。

### D6: 变更事件 — Spring ApplicationEvent（同步 + 异步可切换）

**选择**：定义 `KbDocumentChangedEvent`（path、changeType、commitHash、repositoryId），Application 层通过 `ApplicationEventPublisher.publishEvent` 发布；本 change 不实现监听者（属于 #12 范围）。

**理由**：
- Spring 原生事件机制 = 0 引入成本
- 监听者可选 `@EventListener`（同步）或 `@TransactionalEventListener` + `@Async`（异步）；#12 自己决定
- 单测可注册 `@EventListener` 验证事件正确发出，不依赖真实索引器

**备选**：直接调用 #12 服务接口——耦合本 change 与 #12 的代码组织；用 MQ（Kafka/RabbitMQ）—— 单实例无必要、引入额外基础设施。

### D7: Clone 路径 — 应用工作目录下 `${kb.git.clone-base-dir:./kb-data}/<repository-id>`

**选择**：仓库 id 作为子目录名，base dir 可配置；目录不存在自动创建。

**理由**：
- id 隔离避免不同仓库混淆
- base dir 可配置便于运维把 clone 目录放到大盘上
- 删除仓库配置时同时清理目录（在删除 API 内做）

### D8: API 设计 — 资源风格 + 触发动作风格混用

**选择**：
- `POST /api/v1/kb/repositories` 创建（系统至多 1 个）
- `GET /api/v1/kb/repositories` 列表（所有登录用户可读，但凭据字段对非 ADMIN 不返回）
- `GET /api/v1/kb/repositories/{id}` 详情
- `PUT /api/v1/kb/repositories/{id}` 更新（ADMIN）
- `DELETE /api/v1/kb/repositories/{id}` 删除（ADMIN，同步清理本地 clone）
- `POST /api/v1/kb/repositories/{id}/sync` 触发立即同步（ADMIN）

**理由**：与项目已有的 reviewer/review-style 风格一致；sync 是动词，单独 endpoint。

## Risks / Trade-offs

- **[Git 远端不可达]** → 任务标记 ERROR，下一轮重试；记录 last_error_message。Mitigation：错误状态对外可见，运维及时介入；不阻塞应用启动。
- **[凭据明文存储]** → 数据库 dump 泄漏即凭据泄漏。Mitigation：D2 已讨论；列为 Tech Debt，未来用 Jasypt 加密字段；当前依赖数据库访问控制 + 日志 mask。
- **[首次 clone 时间长]** → 大型知识库 clone 可能超过 HTTP 超时。Mitigation：clone 用独立长超时（默认 5 分钟，可配）；首次 clone 在后台线程执行，不阻塞配置 API 返回。
- **[本地工作区被 reset 删数据]** → 用户在本地目录手工改文件会被覆盖。Mitigation：文档明确"本地目录是只读镜像"；运维不要碰。
- **[轮询频率太高被 git 服务限流]** → Mitigation：默认 1 小时足够保守，可配；ADMIN 需要即时同步可走"立即同步"接口（D8）；后续支持 webhook 后频率可降为兜底。
- **[ApplicationEvent 同步发布失败时 #12 索引失败]** → 当前 change 不感知 #12 实现，事件发布失败可能阻塞同步任务。Mitigation：监听者用 `@Async` 解耦；如同步监听抛错，任务本身仍标记 HEALTHY（变更下次 pull 还能重发）。
