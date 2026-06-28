## 1. 配置与 Properties

- [x] 1.1 在 `KbGitProperties` 新增字段：`private long fetchTimeoutMs = 60_000L;` 及对应 getter
- [x] 1.2 在 `bootstrap/src/main/resources/application.yml` 的 `kb.git` 段新增 `fetch-timeout-ms: 60000`

## 2. Infrastructure 层 — Git 操作超时

- [x] 2.1 修改 `GitOperations.cloneRepository(...)`：构造 `CloneCommand` 后调用 `.setTimeout((int) Math.max(1, properties.getCloneTimeoutMs() / 1000))`
- [x] 2.2 修改 `GitOperations.fetchAndReset(...)`：构造 `FetchCommand` 后调用 `.setTimeout((int) Math.max(1, properties.getFetchTimeoutMs() / 1000))`
- [x] 2.3 确认 `GitOperations` 已注入 `KbGitProperties`（若未注入则补构造器参数 + `@RequiredArgsConstructor`）

## 3. Domain + Infrastructure — 查询接口

- [x] 3.1 在 `KbRepositoryRepository` 接口新增方法：`List<KbRepository> findAllSyncing();`
- [x] 3.2 在 `KbRepositoryRepositoryImpl` 实现 `findAllSyncing`：`LambdaQueryWrapper.eq(syncStatus, "SYNCING")` → 用 Assembler 转聚合根列表

## 4. Application 层 — 启动清理

- [x] 4.1 创建 `application/.../knowledgebase/git/service/KbSyncStartupCleanup.java`：
  ```java
  @Component @Slf4j @RequiredArgsConstructor
  public class KbSyncStartupCleanup implements ApplicationRunner {
      private final KbRepositoryRepository repository;
      @Override
      public void run(ApplicationArguments args) {
          List<KbRepository> stuck = repository.findAllSyncing();
          for (KbRepository repo : stuck) {
              repo.markError("startup cleanup: stale SYNCING from previous shutdown/crash");
              repository.update(repo);
              log.warn("[KB-Startup] cleared stale SYNCING id={} name={}", repo.getId(), repo.getName());
          }
          if (!stuck.isEmpty()) {
              log.info("[KB-Startup] cleaned {} stale SYNCING repository(s)", stuck.size());
          }
      }
  }
  ```

## 5. 测试

- [x] 5.1 `GitOperationsTest` 增加超时用例：
  - 用本地不可达端口（启动 `ServerSocket` 绑定但不 `accept()`，或直接用 `http://127.0.0.1:1`）模拟 hang
  - 配置 `cloneTimeoutMs=2000` / `fetchTimeoutMs=1000`
  - 调用 clone/fetch 应在 timeout + 1s 内抛 `BizException(KB_GIT_CLONE_FAILED/PULL_FAILED)`，message 含 "timeout" 或 "timed out"（不区分大小写）
  - 验证现有 8 个用例继续通过
- [x] 5.2 创建 `KbSyncStartupCleanupTest`（application 单元测试）：
  - Mock `KbRepositoryRepository.findAllSyncing()` 返回 2 个仓库 → 验证 `markError` 被各调用一次且消息含 "startup cleanup" → 验证 `repository.update()` 被各调用一次
  - Mock 返回空列表 → 验证无任何 update 调用
  - 用 `ArgumentCaptor` 验证 markError 传入的消息字符串

## 6. 集成验证（手工）

- [x] 6.1 手工 SQL 模拟残留状态：`UPDATE kb_repository SET sync_status='SYNCING' WHERE id=1;`
- [x] 6.2 重启应用 → 检查启动日志含 `[KB-Startup] cleared stale SYNCING id=1`
- [x] 6.3 查询 DB：`SELECT sync_status, last_error_message FROM kb_repository WHERE id=1;` → 预期 `sync_status='ERROR'`，`last_error_message` 含 `startup cleanup`
- [x] 6.4 等待下一次 `KbSyncScheduler` 轮询（或临时改 `kb.git.poll-interval-ms=60000` 加速验证）→ 应正常 fetch，状态转 `HEALTHY`

## 7. 归档准备

- [x] 7.1 跑全量测试：`mvn clean test`，确认所有现有 #11 测试 + 新增测试全部通过
- [x] 7.2 运行 `/opsx:sync` 把 spec 增量合并到主 spec（`openspec/specs/kb-git-watcher/spec.md` 末尾追加 2 条新 requirement）
- [x] 7.3 运行 `/opsx:archive` 归档本 change，在 `openspec/roadmap.md` 阶段三表格下方注明本修复 change（不占用主 # 编号）
