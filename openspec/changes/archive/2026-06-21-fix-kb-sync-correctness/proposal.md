## Why

2026-06-21 端到端验证 #11(`add-kb-git-watcher`)时,创建仓库 → 首次同步 → 错误状态全流程暴露了两个真实 bug。修复必须做,否则任何"clone 失败"场景(空仓库 / 凭据错 / URL 错)都会让仓库**永久卡在 SYNCING**,只能靠下次重启的启动清理救——用户体验和可观测性都不可接受。

### Bug A — create 异步触发与事务提交存在竞态

`KbRepositoryApplicationService.create()` 是 `@Transactional`,在事务内调 `syncTaskService.executeAsync(saved.getId())`。`@Async` 立即在另一个线程跑,但 create 事务还没 commit,新线程 `findById` 找不到记录,日志:

```
11:46:16 [tomcat-handler-4] [KB-Repo] created id=2
11:46:16 [kb-sync-1]        [KB-Sync] repository not found id=2  ← 静默跳过
```

后果:**首次同步永远不发生**,要等 1 小时后 `KbSyncScheduler` 周期触发才补救。

### Bug B — 乐观锁与 domain 富血模型不对接(更严重)

`KbRepositoryRepositoryImpl.update()` 调用 `mapper.updateById(po)` 后,MyBatis-Plus 把 PO 的 version 自增了,但 **domain repo 对象的 version 字段没有反写**。`KbSyncTaskService.execute()` 流程的实际表现:

```
repo.markSyncing()         → version 字段仍是初始值 N
repository.update(repo)    → DB 从 N 改为 N+1，但 repo.version 仍是 N  ⚠️
...clone 抛 BizException...
repo.markError(msg)        → 版本字段没变
repository.update(repo)    → SET version=N+1 WHERE version=N
                            DB 已经是 N+1 → Updates: 0 静默失败
```

实测日志:
```
11:46:58.409 SET sync_status=ERROR, version=3 WHERE id=2 AND version=2
11:46:58.410 <== Updates: 0   ← markError 静默失败
DB: sync_status='SYNCING' last_error_message=NULL  ← 错误信息永远丢失
```

后果:任何"先 markSyncing 再 markError/markHealthy"的失败路径都让仓库**卡死在 SYNCING + 无错误信息**,且看不出原因。原本 `KbSyncTaskService` 的 catch 块设计是为了"把错误持久化到 DB",现在完全失效。

## What Changes

- **`KbRepositoryRepository.update()` 签名改为返回 `KbRepository`** — 带最新 version 的 domain 对象
- **`KbRepositoryRepositoryImpl.update()` 在 `mapper.updateById` 后用 Assembler 把更新后的 PO 转回 domain 返回**
- **`KbSyncTaskService.execute()` 里所有连续 update 改为 `repo = repository.update(repo)`** — 拿到带新 version 的对象用于后续 markError/markHealthy
- **`KbRepositoryApplicationService.create()` 用 `TransactionSynchronizationManager.registerSynchronization` 把 `executeAsync` 包在 `afterCommit` 里** — 事务提交后才触发首次同步
- **kb-git-watcher spec 增量** — 在"同步状态机"加 update 必须返回最新 version 的约束;在仓库 CRUD API 加"create 必须在事务提交后触发同步"约束
- **测试** — 验证 update 返回值的 version 正确自增;验证连续 markSyncing+markError 不冲突;验证 create 异步触发时机

## Capabilities

### New Capabilities

(无)

### Modified Capabilities

- `kb-git-watcher`:`Repository.update()` 契约扩展(返回最新 version);ADMIN 创建仓库 scenario 增加事务提交后触发同步的约束

## Impact

- **代码**:
  - `domain/.../knowledgebase/git/repository/KbRepositoryRepository.java` — `update()` 返回类型 `void → KbRepository`
  - `infrastructure/.../knowledgebase/git/repository/KbRepositoryRepositoryImpl.java` — `update()` 在 updateById 后用 Assembler 转 PO → domain 返回
  - `application/.../knowledgebase/git/service/KbSyncTaskService.java` — `execute()` 连续 update 路径接收返回值
  - `application/.../knowledgebase/git/service/KbRepositoryApplicationService.java` — `create()` 用 `TransactionSynchronizationManager` 包装异步触发;其他单次 update 处(`update` / `delete` / `triggerSync`)可选接收返回值
  - `application/.../knowledgebase/git/service/KbSyncStartupCleanup.java` — `cleanup` 处的 update 调用可选接收(单次 update,影响小)
- **spec**:`openspec/specs/kb-git-watcher/spec.md` 增加 1 条 MODIFIED Requirement + 1 条 ADDED scenario
- **测试**:
  - 新增 `KbRepositoryRepositoryImplTest`(`@SpringBootTest` + H2 或真实 MySQL)— 验证 update 返回的 version 已自增
  - `KbSyncTaskServiceTest` 增加用例:连续 markSyncing + markError 不冲突,DB 最终状态 ERROR + last_error_message 非空
  - `KbRepositoryApplicationServiceTest` 增加用例:create 时 `syncTaskService.executeAsync` 在事务提交后调用(用 `ArgumentCaptor` + `verify(...).after(<事务提交>)`)
- **无 DB schema 变更,无 API 契约变更,无前端变更**
- **向后兼容**:`update()` 返回值是新增,旧调用方可以忽略返回值(只是不刷新本地对象的 version)

## Out of Scope

- **不改 markError catch 流程** — catch 块本身没问题,问题在 update 返回值
- **不重构 domain 富血模型暴露 setter** — 通过 update 返回新对象的方式同步,保持封装
- **不引入 ORM 自动反向 sync 机制** — KISS:手动返回 + 接收即可
- **不处理事件投递可靠性** — 仍留到 #12 设计阶段
- **不改 #11 已有的"SYNCING 时跳过新一轮调度"规则** — 该规则与本修复无关
