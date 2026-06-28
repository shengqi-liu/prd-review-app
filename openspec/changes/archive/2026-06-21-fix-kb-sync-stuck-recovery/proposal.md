## Why

#11 (`add-kb-git-watcher`) 引入的同步任务有两个会让 `sync_status='SYNCING'` **永远卡住**的漏洞，2026-06-21 explore 时发现：

1. **JGit 操作没有 timeout 保护** — `GitOperations.cloneRepository` / `fetchAndReset` 完全没调 `.setTimeout()`；配置项 `KbGitProperties.cloneTimeoutMs` 定义了但**从未被任何代码读取**。一旦远端网络 hang（SSL 握手卡死 / 远端缓慢响应），git 操作无限阻塞。
2. **JVM crash 后状态不会自动恢复** — `markSyncing()` 写入 DB 之后，若 JVM crash / kill -9 / 容器重启，进程在 `markHealthy/markError` 之前没了，`sync_status` 永远停在 `SYNCING`。重启后所有调度因状态机保护（"SYNCING 时跳过本轮"）全部跳过，需要运维手工 SQL 改回 `HEALTHY` 才能恢复。

两个漏洞在生产环境都是高概率事件（网络抖动、容器调度、滚动更新），必须修复。

## What Changes

- **GitOperations 真正使用 timeout 配置** — clone 用 `KbGitProperties.cloneTimeoutMs`（已存在），fetch 用新增的 `fetchTimeoutMs`（默认 60s）；统一传给 JGit 的 `TransportCommand.setTimeout(int seconds)`，单位换算 `ms → seconds` 且最小值 1 秒
- **KbGitProperties 新增 `fetchTimeoutMs` 配置**（默认 60_000）
- **新增 KbSyncStartupCleanup** — 实现 `ApplicationRunner`，应用启动时一次性把所有 `sync_status='SYNCING'` 的记录改为 `ERROR`，`lastErrorMessage` 含 "startup cleanup" 关键字
- **新增 `KbRepositoryRepository.findAllSyncing()` 方法** — 供启动清理使用
- **kb-git-watcher spec 增量** — 新增"Git 操作超时保护" + "启动时清理残留 SYNCING 状态" 两条 requirement

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `kb-git-watcher`：新增 git 操作超时与启动清理两条 requirement

## Impact

- **代码**：
  - `infrastructure/.../jgit/GitOperations.java` — clone/fetch 传入 timeout
  - `infrastructure/.../KbGitProperties.java` — 新增 `fetchTimeoutMs` 字段 + getter
  - `application/.../service/KbSyncStartupCleanup.java` — 新增启动清理类（约 20 行）
  - `domain/.../repository/KbRepositoryRepository.java` — 新增 `findAllSyncing()` 方法
  - `infrastructure/.../repository/KbRepositoryRepositoryImpl.java` — 实现 `findAllSyncing()`
- **数据库**：无 schema 变更
- **配置**：`application.yml` 新增 `kb.git.fetch-timeout-ms: 60000`
- **spec**：`openspec/specs/kb-git-watcher/spec.md` 通过 ADDED 增量加 2 条 requirement
- **测试**：
  - `GitOperationsTest` — 用本地不可达端口模拟 hang，验证 timeout 触发
  - `KbSyncStartupCleanupTest` — Mock Repository 验证启动清理把 SYNCING 改 ERROR
- **无 API 变更，无前端变更**（启动清理对前端透明；前端显示从"同步中…"自动变为"同步失败 startup cleanup..."，无需改动）

## Out of Scope

- **不引入 watchdog 定时巡检** — 单实例部署下，timeout（封堵 git hang）+ 启动清理（封堵 JVM crash 残留）已覆盖所有现实场景。watchdog 独自覆盖的"运行中代码 bug 漏 markError"概率极低（KbSyncTaskService 已有双层 catch 兜底），不值得为它新增表列/调度/测试
- **不处理事件投递可靠性**（#1 隐忧，已在 roadmap #12 节标注，留到 #12 设计阶段做透）
- **不引入分布式锁** — 单实例部署足够
- **不重试卡死的 git 操作** — 启动清理只负责把状态打回 ERROR，下一轮 `@Scheduled` 调度自然重试
- **不加 `statusChangedAt` 列** — 启动清理基于"SYNCING + 进程刚启动"双重信号，不依赖时间戳判定
