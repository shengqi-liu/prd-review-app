## 0. 前置：错误码与依赖

- [x] 0.1 在 `ErrorCode` 枚举 50000 段新增 `KB_GIT_CLONE_FAILED(50004, "Git 仓库 clone 失败")`、`KB_GIT_PULL_FAILED(50005, "Git 仓库拉取失败")`、`KB_GIT_AUTH_FAILED(50006, "Git 凭据无效或被拒绝")`、`KB_REPO_ALREADY_CONFIGURED(50007, "知识库仓库已配置（系统至多 1 个）")`；运行 `ErrorCodeTest` 验证唯一性
- [x] 0.2 在 parent `pom.xml` 的 `<dependencyManagement>` 中引入 `org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r`（最新稳定版）；在 `infrastructure/pom.xml` 添加 `org.eclipse.jgit:org.eclipse.jgit` + `org.eclipse.jgit:org.eclipse.jgit.ssh.apache`（SSH 支持）

## 1. 数据库迁移

- [x] 1.1 创建 `db/migration/V6__create_kb_repository_table.sql`：建 `kb_repository` 表（id BIGINT AUTO_INCREMENT, name VARCHAR(100) NOT NULL, remote_url VARCHAR(500) NOT NULL, branch VARCHAR(100) NOT NULL DEFAULT 'main', local_path VARCHAR(500) NOT NULL, auth_type VARCHAR(20) NOT NULL DEFAULT 'NONE', auth_secret VARCHAR(1000), poll_interval_ms BIGINT NOT NULL DEFAULT 3600000, sync_status VARCHAR(20) NOT NULL DEFAULT 'HEALTHY', last_synced_commit VARCHAR(40), last_synced_at DATETIME, last_error_message VARCHAR(1000), version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0, created_at DATETIME, updated_at DATETIME, PRIMARY KEY(id)）；添加唯一索引 `uk_kb_repository_name`（name, deleted）；DDL 使用 `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`
- [x] 1.2 执行迁移：`mysql -uroot -p<pwd> --default-character-set=utf8mb4 prd_review < db/migration/V6__create_kb_repository_table.sql`

## 2. Domain 层

- [x] 2.1 创建 `domain/.../knowledgebase/git/model/SyncStatus.java` 枚举：`HEALTHY / SYNCING / ERROR`
- [x] 2.2 创建 `domain/.../knowledgebase/git/model/AuthType.java` 枚举：`NONE / HTTPS_TOKEN / SSH_KEY_PATH`
- [x] 2.3 创建 `domain/.../knowledgebase/git/model/ChangeType.java` 枚举：`ADDED / MODIFIED / DELETED`
- [x] 2.4 创建 `domain/.../knowledgebase/git/model/KbRepository.java` 聚合根：字段见 spec；静态工厂 `create(name, remoteUrl, branch, authType, authSecret, pollIntervalMs, localPath)` → syncStatus=HEALTHY、version=1；`reconstruct(...)` 重建
- [x] 2.5 在 `KbRepository` 实现状态转移行为：`markSyncing()`（HEALTHY/ERROR → SYNCING）、`markHealthy(commitHash)`（SYNCING → HEALTHY，清空 lastErrorMessage）、`markError(message)`（任意 → ERROR，写 lastErrorMessage）
- [x] 2.6 在 `KbRepository` 实现 `update(...)` 方法（更新可变配置字段，触发本地路径变更判断等）与 `markDeleted()`
- [x] 2.7 创建 `domain/.../knowledgebase/git/event/KbDocumentChangedEvent.java`（不可变 record：`Long repositoryId`、`String path`、`ChangeType changeType`、`String commitHash`）
- [x] 2.8 创建 `domain/.../knowledgebase/git/repository/KbRepositoryRepository.java` 接口：findById、findActive（单仓库）、save、update、softDelete、existsActive

## 3. Infrastructure 层 — 持久化

- [x] 3.1 创建 `infrastructure/.../knowledgebase/git/po/KbRepositoryPO.java`（`@TableName("kb_repository")`、`@Version`、`@TableLogic`，字段与表结构对应；syncStatus/authType 用 String 存）
- [x] 3.2 创建 `infrastructure/.../knowledgebase/git/mapper/KbRepositoryMapper.java`（继承 `BaseMapper<KbRepositoryPO>`）
- [x] 3.3 创建 `infrastructure/.../knowledgebase/git/assembler/KbRepositoryAssembler.java`：PO ↔ 聚合根双向转换，处理枚举 ↔ String
- [x] 3.4 实现 `infrastructure/.../knowledgebase/git/repository/KbRepositoryRepositoryImpl.java`：`findActive()` 查询第一条 `deleted=0` 的记录（约定单仓库）；`existsActive()` 检查 `count(*)`

## 4. Infrastructure 层 — Git 操作

- [x] 4.1 创建 `infrastructure/.../knowledgebase/git/jgit/GitOperations.java`：封装 JGit 接口
  - `clone(remoteUrl, branch, localPath, authType, authSecret)` → 返回 HEAD commit hash
  - `fetchAndReset(localPath, branch, authType, authSecret)` → 返回 HEAD commit hash
  - `diffMarkdownChanges(localPath, oldCommit, newCommit)` → `List<MarkdownChange>`（含 path、ChangeType）；旧 commit 为 null 时返回 HEAD 下所有 `.md` 文件为 ADDED
  - `deleteWorkspace(localPath)` → 递归删除目录
  - 内部不打印 `authSecret`，凭据失败转抛 `BizException(KB_GIT_AUTH_FAILED)`，其他失败按 clone/pull 分类抛
- [x] 4.2 创建 `infrastructure/.../knowledgebase/git/jgit/CredentialFactory.java`：根据 AuthType 构造 `UsernamePasswordCredentialsProvider`（HTTPS_TOKEN）或 SshSessionFactory（SSH_KEY_PATH）
- [x] 4.3 创建 `infrastructure/.../knowledgebase/git/jgit/MarkdownChange.java` record（path、ChangeType）

## 5. Application 层 — 同步用例

- [x] 5.1 创建 Command/Query：`CreateKbRepositoryCommand`、`UpdateKbRepositoryCommand`（含 version）、`KbRepositoryQueryCommand`（currentUserRole 用于决定是否 mask）
- [x] 5.2 创建 `KbRepositoryDTO` record（id、name、remoteUrl、branch、authType、**authSecretMasked**、pollIntervalMs、syncStatus、lastSyncedCommit、lastSyncedAt、lastErrorMessage、version、createdAt、updatedAt——authSecret 永远不出现在 DTO 中）
- [x] 5.3 实现 `KbRepositoryApplicationService.create()`：校验单仓库约束（existsActive=true 时抛 `KB_REPO_ALREADY_CONFIGURED`），调用 `KbRepository.create()`，Repository 保存，**异步**触发首次同步（不阻塞 API 返回）
- [x] 5.4 实现 `KbRepositoryApplicationService.update()`：查找仓库 → 检查 remoteUrl/branch 是否变更（若变更则下次同步前清理本地目录）→ 调用 `repo.update()` → Repository 乐观锁更新
- [x] 5.5 实现 `KbRepositoryApplicationService.delete()`：查找仓库 → `repo.markDeleted()` → Repository 软删 → 调用 `GitOperations.deleteWorkspace()` 清理本地
- [x] 5.6 实现 `KbRepositoryApplicationService.getById()` 与 `listRepositories()`：按角色 mask `authSecret`（非 ADMIN 返回 `***`）
- [x] 5.7 实现 `KbRepositoryApplicationService.triggerSync(repositoryId)`：CAS 检查 syncStatus（已是 SYNCING 直接返回当前状态），否则异步触发 `KbSyncTaskService.execute(repositoryId)`
- [x] 5.8 创建 `KbSyncTaskService`（应用服务）：核心同步用例
  - `execute(repositoryId)`：`@Transactional` 加 `Propagation.REQUIRES_NEW`；查找仓库 → `markSyncing()` + 立即持久化 → 调用 `GitOperations.clone` 或 `fetchAndReset` → 调用 `diffMarkdownChanges` → 通过 `ApplicationEventPublisher.publishEvent` 发布 `KbDocumentChangedEvent`（每个变更一条；RENAMED 拆 DELETED+ADDED）→ `markHealthy(newCommit)` + 持久化
  - 异常路径：捕获 `BizException`，调用 `repo.markError(ex.getMessage())` + 持久化，日志 mask 凭据后输出 WARN
- [x] 5.9 创建 `KbSyncScheduler`：`@Scheduled(fixedDelayString = "${kb.git.poll-interval-ms:3600000}")`，调用 `repository.findActive()`；存在仓库则触发 `KbSyncTaskService.execute()`，否则空跑

## 6. API 层

- [x] 6.1 创建请求 DTO：`CreateKbRepositoryRequest`（@NotBlank name 100, @NotBlank @URL remoteUrl, branch 默认 main, authType, authSecret, pollIntervalMs 默认 3600000）、`UpdateKbRepositoryRequest`（含 @NotNull version）
- [x] 6.2 创建响应 DTO：`KbRepositoryResponse`（所有展示字段 + `authSecretMasked`）；`KbRepositoryStatusResponse`（精简版，仅 id/name/syncStatus/lastSyncedCommit/lastSyncedAt/lastErrorMessage，给前端"状态卡片"用）
- [x] 6.3 实现 `KbRepositoryController`：`POST /api/v1/kb/repositories`（@RequireRole ADMIN）创建
- [x] 6.4 实现 `KbRepositoryController`：`PUT /api/v1/kb/repositories/{id}`（@RequireRole ADMIN）更新
- [x] 6.5 实现 `KbRepositoryController`：`DELETE /api/v1/kb/repositories/{id}`（@RequireRole ADMIN）删除
- [x] 6.6 实现 `KbRepositoryController`：`POST /api/v1/kb/repositories/{id}/sync`（@RequireRole ADMIN）触发立即同步
- [x] 6.7 实现 `KbRepositoryController`：`GET /api/v1/kb/repositories/{id}` 与 `GET /api/v1/kb/repositories`（@RequireRole SUBMITTER 以上，凭据按角色 mask）

## 7. 配置与启动

- [x] 7.1 在 `bootstrap/src/main/resources/application.yml` 新增 `kb.git.poll-interval-ms: 3600000`（轮询间隔 1 小时）、`kb.git.clone-base-dir: ./kb-data`、`kb.git.clone-timeout-ms: 300000`（首次 clone 超时 5 分钟）
- [x] 7.2 在 `bootstrap/src/main/java/com/prdreview/bootstrap/config` 创建 `SchedulingConfig.java`：`@EnableScheduling` + `@EnableAsync`（如未启用）；配置 `ThreadPoolTaskExecutor` 给 `@Async` 同步任务用（核心 2、最大 4、队列 100）
- [x] 7.3 在 `KbGitProperties.java`（infrastructure 层）使用 `@ConfigurationProperties(prefix = "kb.git")` 读取 base-dir / clone-timeout 等配置

## 8. 测试

- [x] 8.1 `KbRepositoryTest`（纯领域单元测试）：create 默认值；状态机转移（HEALTHY→SYNCING→HEALTHY、SYNCING→ERROR）；update 行为；markDeleted
- [x] 8.2 `GitOperationsTest`（infrastructure 集成测试）：用 `Git.init()` 在临时目录创建本地裸仓库 + 工作仓库，写入若干 .md 文件，commit；测试 clone / fetchAndReset / diffMarkdownChanges 行为；覆盖 ADDED、MODIFIED、DELETED、RENAMED 拆分、非 .md 文件忽略；不依赖外部网络
- [x] 8.3 `KbSyncTaskServiceTest`（application 单元测试）：Mock Repository + GitOperations + ApplicationEventPublisher
  - 首次同步：clone 成功 → 全量 .md 发 ADDED 事件 → markHealthy
  - 后续同步：fetchAndReset → diff → 每个 MarkdownChange 发对应事件
  - 凭据失败：捕获 `KB_GIT_AUTH_FAILED` → markError，事件不发布
- [x] 8.4 `KbRepositoryApplicationServiceTest`：
  - create 成功 + 已存在仓库时抛 `KB_REPO_ALREADY_CONFIGURED`
  - update 乐观锁冲突 + remoteUrl 变更需重 clone 的标记
  - delete 调用 `deleteWorkspace`
  - getById/list 非 ADMIN 返回的 `authSecretMasked = "***"`
  - triggerSync：HEALTHY 时触发，SYNCING 时跳过
- [x] 8.5 `KbSyncSchedulerTest`：无仓库时空跑；存在仓库时调用 `KbSyncTaskService.execute`

## 9. 前端（最小联调）

- [x] 9.1 修改 `frontend/app.html` 的"知识库"页：把"配置路径"按钮挂上 onclick 打开新建/编辑 Modal；"重建索引"按钮改为"立即同步"，挂上 onclick 调用 `POST /api/v1/kb/repositories/{id}/sync`
- [x] 9.2 创建 `frontend/js/kb-repository.js`：封装 `loadKbStatus()`（拉取仓库状态 + 渲染状态徽标/最后同步时间/错误提示）、`openKbConfigModal()`、`submitKbConfig()`、`triggerKbSync()`
- [x] 9.3 在 `frontend/app.html` 末尾新增"知识库仓库配置" Modal（含 name / remoteUrl / branch / authType / authSecret / pollIntervalMs）；引入 `<script src="js/kb-repository.js"></script>`
- [x] 9.4 在 `navTo()` 钩子加入 `if (name === 'kb') loadKbStatus()`；非 ADMIN 隐藏"配置路径"和"立即同步"按钮
