## 1. Domain 层 — 接口签名变更

- [x] 1.1 修改 `KbRepositoryRepository.update(KbRepository)` 返回类型 `void → KbRepository`，更新 JavaDoc 说明"返回带最新 version 的对象，调用方应替换本地引用以避免后续乐观锁冲突"

## 2. Infrastructure 层 — 实现反写 version

- [x] 2.1 修改 `KbRepositoryRepositoryImpl.update(...)`：在 `mapper.updateById(po)` 之后用 `KbRepositoryAssembler.toDomain(po)` 把已自增 version 的 PO 转回 domain 返回（MyBatis-Plus 的 @Version 注解会在 updateById 内修改 PO 字段，转回时拿到的就是新 version）

## 3. Application 层 — KbSyncTaskService 连续 update 路径

- [x] 3.1 修改 `KbSyncTaskService.execute()`：把 `repo.markSyncing(); repository.update(repo);` 改为 `repo.markSyncing(); repo = repository.update(repo);`（拿到刷新 version 的对象）
- [x] 3.2 验证后续 markHealthy/markError + update 是终态调用，无需再接收返回值（保持原样）

## 4. Application 层 — create() 用 TransactionSynchronizationManager

- [x] 4.1 修改 `KbRepositoryApplicationService.create()`：
  - import `org.springframework.transaction.support.TransactionSynchronization` 和 `TransactionSynchronizationManager`
  - 把 `syncTaskService.executeAsync(saved.getId())` 替换为：
    ```java
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            syncTaskService.executeAsync(saved.getId());
        }
    });
    ```
  - 保持原日志 `[KB-Repo] created id=...` 的位置不变

## 5. 其他调用方（向后兼容验证）

- [x] 5.1 编译验证：所有现有 `repository.update(...)` 调用方继续编译通过（旧调用方忽略返回值，向后兼容）
- [x] 5.2 可选：`KbRepositoryApplicationService.update/delete/triggerSync` 和 `KbSyncStartupCleanup.run` 处只 update 一次，是否接收返回值不影响功能，保持原样

## 6. 测试

- [x] 6.1 `KbRepositoryApplicationServiceTest` 修复：调整 `update(...)` Mock 让其返回入参（`when(repo.update(any())).thenAnswer(inv -> inv.getArgument(0))`），不影响现有用例
- [x] 6.2 `KbSyncTaskServiceTest` 修复：同上调整 Mock 返回值
- [x] 6.3 `KbSyncTaskServiceTest` 新增用例 `markSyncingThenMarkError_persistsErrorState`：
  - Mock `repository.update(any())` 让其模拟乐观锁行为：每次调用返回 version+1 的新 KbRepository 对象
  - 模拟 `gitWatcher.cloneRepository(...)` 抛 `BizException(KB_GIT_CLONE_FAILED)`
  - 调 `execute(repoId)`
  - 验证 `repository.update(...)` 至少被调 2 次：第 1 次入参 syncStatus=SYNCING，第 2 次入参 syncStatus=ERROR + lastErrorMessage 非空
- [x] 6.4 `KbRepositoryApplicationServiceTest` 新增用例 `create_triggersAsyncSyncAfterCommit`：
  - 用 Mockito mock `TransactionSynchronizationManager`（用 `MockedStatic`）
  - 调 `create(cmd)`
  - 验证 `TransactionSynchronizationManager.registerSynchronization` 被调用 1 次，传入的 `TransactionSynchronization.afterCommit()` 会触发 `syncTaskService.executeAsync(savedId)`
- [x] 6.5 `KbRepositoryRepositoryImplTest` 新增 Mockito 单元测试（不引入 SpringBootTest 集成基础设施）：
  - Mock `mapper.updateById(po)` 让其模拟 @Version 行为：`po.setVersion(po.getVersion() + 1); return 1;`
  - 调 `repository.update(domainRepo)`
  - 断言返回值不为 null，且 `version` 等于入参 +1
  - 断言其他字段透传不变

## 7. 端到端验证（手工）

- [x] 7.1 重启应用，再次创建一个会失败的仓库：
  ```bash
  curl -X POST .../api/v1/kb/repositories -d '{"name":"test-bad","remoteUrl":"https://example.com/nope.git","branch":"main","authType":"NONE"}'
  ```
- [x] 7.2 等 5 秒，查 DB：`SELECT id, sync_status, last_error_message FROM kb_repository WHERE name='test-bad';`
- [x] 7.3 预期：`sync_status='ERROR'`（不是 SYNCING），`last_error_message` 含具体错误信息
- [x] 7.4 检查日志，应**不再出现** `[KB-Sync] repository not found id=...`

## 8. 归档准备

- [x] 8.1 跑全量测试 `mvn clean test`，确认无回归
- [x] 8.2 把本 change 的 spec 增量合并到主 `openspec/specs/kb-git-watcher/spec.md`（替换"同步状态机"和"仓库管理 REST API"两条 MODIFIED Requirement 的内容）
- [x] 8.3 归档：`mv openspec/changes/fix-kb-sync-correctness openspec/changes/archive/$(date +%Y-%m-%d)-fix-kb-sync-correctness`
- [x] 8.4 在 `openspec/roadmap.md` 阶段三表格 #11 行下方追加一行 `11.2 | fix-kb-sync-correctness | 修复 #11 乐观锁同步 + 事务时序 | #11 | ✅ DONE | <日期>`
