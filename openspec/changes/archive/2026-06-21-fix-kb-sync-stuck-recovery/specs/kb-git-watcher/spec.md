## ADDED Requirements

### Requirement: Git 操作超时保护
系统 SHALL 给所有 JGit 网络操作（`clone` / `fetch`）传入显式超时，防止远端网络异常导致同步任务无限期阻塞。

- `clone` 操作 SHALL 使用 `kb.git.clone-timeout-ms` 配置（默认 300_000 = 5 分钟），传给 JGit `CloneCommand.setTimeout(int seconds)`，单位换算 `ms → seconds`，最小值 1 秒
- `fetch` 操作 SHALL 使用 `kb.git.fetch-timeout-ms` 配置（默认 60_000 = 1 分钟），传给 JGit `FetchCommand.setTimeout(int seconds)`
- 超时触发时 SHALL 抛出 `BizException(KB_GIT_CLONE_FAILED / KB_GIT_PULL_FAILED)`，由上层 `KbSyncTaskService` 捕获并调用 `markError`

#### Scenario: clone 操作传入正确超时
- **WHEN** `GitOperations.cloneRepository` 被调用
- **THEN** 内部构造的 `CloneCommand` MUST 调用 `.setTimeout(N)`，其中 `N = max(1, cloneTimeoutMs / 1000)`

#### Scenario: fetch 操作传入正确超时
- **WHEN** `GitOperations.fetchAndReset` 被调用
- **THEN** 内部构造的 `FetchCommand` MUST 调用 `.setTimeout(N)`，其中 `N = max(1, fetchTimeoutMs / 1000)`

#### Scenario: 网络 hang 触发超时异常
- **WHEN** 远端不响应（指向不可达的本地端口模拟）且操作超过配置的 timeout
- **THEN** 系统 MUST 在 `timeout + 缓冲` 时间内抛出 `BizException`（`KB_GIT_CLONE_FAILED` 或 `KB_GIT_PULL_FAILED`），错误信息含 `Timeout` 或 `timed out` 关键字

---

### Requirement: 启动时清理残留 SYNCING 状态
系统 SHALL 在应用启动时一次性清理所有 `sync_status = SYNCING` 的仓库记录，将其转为 `ERROR`，避免因上次进程崩溃残留的状态阻塞后续调度。

- 清理 SHALL 由实现 `ApplicationRunner` 接口的 `KbSyncStartupCleanup` 完成，由 Spring 在 ApplicationContext 完全就绪后调用一次
- 清理 SHALL 通过 `KbRepositoryRepository.findAllSyncing()` 获取所有 SYNCING 仓库，对每个调用 `markError("startup cleanup: stale SYNCING from previous shutdown/crash")` 并 `repository.update(repo)` 持久化
- 清理 SHALL 通过 WARN 级别日志输出被清理的仓库 id 与 name，便于运维定位历史问题
- 清理过程 SHALL NOT 调用任何 git/网络操作（保证启动不被慢操作拖延）

#### Scenario: 启动清理把残留 SYNCING 转为 ERROR
- **WHEN** 应用启动时 DB 中存在 `sync_status = SYNCING` 的仓库
- **THEN** `KbSyncStartupCleanup.run()` MUST 将该仓库 `sync_status` 改为 `ERROR`，`lastErrorMessage` 含 `startup cleanup` 关键字

#### Scenario: HEALTHY / ERROR 状态不受启动清理影响
- **WHEN** 应用启动时 DB 中的仓库状态为 `HEALTHY` 或 `ERROR`
- **THEN** 启动清理 MUST 不修改该仓库任何字段

#### Scenario: 启动清理后调度可正常 fetch
- **WHEN** 启动清理把卡死的仓库改为 `ERROR` 后，`KbSyncScheduler` 首次轮询触发
- **THEN** 系统 MUST 正常执行同步流程（fetch / clone），按照 ERROR 不阻塞同步的既有规则
