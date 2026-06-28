## MODIFIED Requirements

### Requirement: 同步状态机
系统 SHALL 在每次同步任务前后维护 `syncStatus` 状态机：`HEALTHY → SYNCING → HEALTHY/ERROR → SYNCING`。状态为 `SYNCING` 时定时任务 SHALL 跳过本轮，防止并发同步。

`ERROR` 状态 SHALL 不阻塞下一轮同步——下次定时触发时系统重新尝试，并在 `lastErrorMessage` 写入最新错误内容。

`KbRepositoryRepository.update(KbRepository)` SHALL 返回带最新 `version` 的 domain 对象。调用方在同一执行流内若需要再次 update 同一聚合根，MUST 使用返回值替换本地引用，否则乐观锁条件 `WHERE version = ?` 会失配，update 静默失败 0 行（导致状态卡死、错误信息丢失）。

#### Scenario: 正常同步完成回到 HEALTHY
- **WHEN** 一次定时同步任务执行成功（无论是否检测到变更）
- **THEN** `syncStatus` MUST 为 `HEALTHY`，`lastSyncedCommit` MUST 更新为最新 commit，`lastErrorMessage` MUST 为 null

#### Scenario: 同步失败置 ERROR
- **WHEN** 一次同步任务因 git pull/clone/auth 异常失败
- **THEN** `syncStatus` MUST 为 `ERROR`，`lastErrorMessage` MUST 含异常摘要，`lastSyncedCommit` MUST 保持上一次成功的值

#### Scenario: SYNCING 时跳过新一轮调度
- **WHEN** 定时任务触发时仓库 `syncStatus` 已是 `SYNCING`
- **THEN** 系统 MUST 跳过本轮，记录 DEBUG 日志，不抛出异常

#### Scenario: 连续 markSyncing + markError 不会因乐观锁冲突而静默失败
- **WHEN** `KbSyncTaskService.execute()` 流程先调 `markSyncing()` + `update()`，随后因 git 失败再调 `markError()` + `update()`
- **THEN** 第二次 `update` MUST 影响 1 行（不能 0 行），DB 中 `sync_status` MUST 为 `ERROR`，`last_error_message` MUST 含异常摘要

#### Scenario: update 返回的 domain 对象 version 已自增
- **WHEN** 调用 `KbRepositoryRepository.update(repo)` 且 update 成功（影响 1 行）
- **THEN** 返回值 MUST 是一个 `KbRepository` 实例，其 `version` 字段 MUST 等于入参 version + 1

---

### Requirement: 仓库管理 REST API
系统 SHALL 提供 `/api/v1/kb/repositories` 资源接口支持仓库的 CRUD 与立即同步。

写操作（创建、更新、删除、立即同步）SHALL 仅限 ADMIN 角色。读操作（列表、详情）SHALL 对所有已登录用户开放，但响应中 `authSecret` 字段 SHALL 对非 ADMIN 用户做 mask（返回 `"***"` 或 null）。

`POST /api/v1/kb/repositories` 触发的首次异步同步 SHALL 在创建事务**提交之后**才发起。`@Transactional` 内直接调用 `@Async` 方法存在竞态——异步任务可能在事务提交前抢先执行，导致 `findById` 找不到刚 save 的记录、首次同步被静默跳过。系统 SHALL 使用 `TransactionSynchronizationManager.registerSynchronization` 在 `afterCommit` 阶段触发 `executeAsync`。

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

#### Scenario: 创建仓库的异步同步在事务提交后才发起
- **WHEN** ADMIN 调用 `create()`，事务进入提交阶段
- **THEN** `syncTaskService.executeAsync(id)` MUST 在 `afterCommit` 回调中调用（不能在事务方法体内直接调用），保证异步线程的 `findById` 能找到刚 save 的记录

#### Scenario: 普通用户查询凭据被 mask
- **WHEN** SUBMITTER 发送 `GET /api/v1/kb/repositories/{id}`
- **THEN** 响应 MUST 不暴露 `authSecret` 真实值（返回 null 或 `"***"`）

#### Scenario: 非 ADMIN 尝试写操作
- **WHEN** SUBMITTER 或 TEAM_MEMBER 尝试 POST/PUT/DELETE/sync
- **THEN** 系统 MUST 返回 `FORBIDDEN` 错误
