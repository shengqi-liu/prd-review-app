## Context

#11 的同步链路两个 bug 在端到端验证时被一次性挖出:
- **Bug A**:`@Transactional + @Async` 的经典陷阱——异步任务在外层事务提交前跑,看不到刚 save 的记录
- **Bug B**:DDD 富血模型 + ORM 乐观锁的一个**反直觉死角**——MyBatis-Plus 的 `@Version` 是基于 PO 工作的,update 后会自增 PO 内字段,但 PO 是 domain 的一份 *拷贝*,domain 本体不会被同步

两个 bug 加在一起,任何"先 markSyncing 再 markError"的失败路径都会让仓库永久卡死。当前唯一的兜底是 `fix-kb-sync-stuck-recovery` 引入的启动清理——治标不治本。本 change 修根因。

## Goals / Non-Goals

**Goals:**
- `KbRepositoryRepository.update()` 调用方拿到的 domain 对象始终持有最新 version,后续 update 不会乐观锁冲突
- `create()` 触发的首次同步必然能找到刚创建的记录,不再静默跳过
- 修复对外部 API 透明,对前端透明,无 DB schema 变更
- 单元 + 集成测试覆盖这两条修复路径,防回归

**Non-Goals:**
- 不重构 domain 富血模型让外部 setter 可见(保持封装)
- 不引入第三方 ORM 框架(纯靠 MyBatis-Plus + 手动 Assembler)
- 不改 markError/markHealthy/markSyncing 的方法签名
- 不修改 #11 已有的事件发布契约(`KbDocumentChangedEvent` 保持不变)

## Decisions

### D1:`update()` 返回 `KbRepository` 而非 void

**选择**:`KbRepositoryRepository.update(KbRepository)` 返回类型从 `void` 改为 `KbRepository`(带 update 后的最新 version)。

**理由**:
- 这是 Spring Data / JPA 的标准模式(`save(entity)` 返回 managed entity)
- 调用方语义清晰:"用完这次,继续用返回的对象,不要用旧的"
- 旧调用方可以忽略返回值,只是不刷新自己的本地副本——**向后兼容**
- KbSyncTaskService 这种需要连续 update 的场景,必须接收返回值

**备选 A**:让 domain 暴露 `package-private setVersion(int)` 给 Assembler 用——破坏封装,且 Assembler 在不同模块包,package-private 不可见,得改成 `public`,危险
**备选 B**:Infra 层用反射设 version 字段——黑魔法,测不友好
**备选 C**:每次 update 后让调用方主动 `findById` 重新拿——浪费一次 DB round-trip,且语义生硬

### D2:Infra 层实现:updateById 后用 Assembler 转回

实现要点:
```java
@Override
public KbRepository update(KbRepository repository) {
    KbRepositoryPO po = KbRepositoryAssembler.toPO(repository);
    mapper.updateById(po);   // MyBatis-Plus 自增 po.version
    return KbRepositoryAssembler.toDomain(po);  // 转回 domain,version 已最新
}
```

关键:`mapper.updateById(po)` 调用后,MyBatis-Plus 会在 `po.version` 字段自增(MyBatis-Plus `@Version` 的标准行为),所以 `toDomain(po)` 拿到的是已自增的 version。

**注意**:如果 update 因乐观锁失败(影响 0 行),MyBatis-Plus 会抛 `OptimisticLockingFailureException`(项目已在 `GlobalExceptionHandler` 拦截转 `PRD_VERSION_CONFLICT`)。本场景应永不抛出——因为我们刚通过 `findById` 拿到了最新 version。如果抛了,说明有并发修改,正确行为是向上传播。

### D3:`create()` 用 `TransactionSynchronizationManager.registerSynchronization`

**选择**:
```java
@Transactional
public KbRepositoryDTO create(CreateKbRepositoryCommand cmd) {
    ...
    KbRepository saved = repository.save(repo);
    log.info("[KB-Repo] created id={} ...", saved.getId(), ...);

    // 关键:在事务提交后才触发异步,避免 executeAsync 抢跑
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                syncTaskService.executeAsync(saved.getId());
            }
        }
    );
    return toDTO(saved, true);
}
```

**理由**:
- Spring 原生 API,无需引入新事件类或框架
- 语义明确:"事务提交后我再异步触发同步"
- 单元测试可 mock `TransactionSynchronizationManager` 或用 `TransactionTemplate` 模拟

**备选 A**:用 `@TransactionalEventListener(AFTER_COMMIT)` 模式——需要新增 `KbRepositoryCreatedEvent` 类 + 事件监听器,改动面大,语义反而绕(明明就是触发首次同步,搞个事件太正式)
**备选 B**:把 `executeAsync` 调用从 service 搬到 controller,在事务外调——破坏 service 自治,逻辑泄漏到 controller
**备选 C**:Service 方法不开事务,手动管事务边界——破坏 `@Transactional` 范式

### D4:`KbSyncTaskService.execute()` 的最小修改

当前流程:
```java
KbRepository repo = repository.findById(repositoryId);
...
repo.markSyncing();
repository.update(repo);          // ← 这次 update 后 repo.version 没刷新

String newCommit;
try {
    newCommit = gitWatcher.cloneRepository(...);
    ...
    repo.markHealthy(newCommit);
    repository.update(repo);      // ← 失败:WHERE version=旧值
} catch (BizException ex) {
    repo.markError(...);
    repository.update(repo);      // ← 失败:WHERE version=旧值
} catch (Exception ex) {
    repo.markError(...);
    repository.update(repo);      // ← 失败:同上
}
```

修复后:
```java
KbRepository repo = repository.findById(repositoryId);
...
repo.markSyncing();
repo = repository.update(repo);   // ← 刷新

try {
    ...
    repo.markHealthy(newCommit);
    repository.update(repo);      // ← OK,无后续 update
} catch (BizException ex) {
    repo.markError(...);
    repository.update(repo);      // ← OK
} catch (Exception ex) {
    repo.markError(...);
    repository.update(repo);      // ← OK
}
```

只有 `markSyncing → markError/markHealthy` 这条路径有连续 update;markError/markHealthy 都是终态,无后续 update,可不接收返回值。

### D5:其他单次 update 处可选接收

`KbRepositoryApplicationService.update()` / `triggerSync()` / `delete()` / `KbSyncStartupCleanup.run()` 都只 update 一次,不接收返回值不会出 bug。为代码风格统一,**建议但不强制**接收。本 change 不强制改这些点,避免触及不必要的代码。

### D6:测试策略

- **单元层(Mockito)**:验证 service 调用 `update` 后用返回值替换 local 变量
- **集成层**:`KbRepositoryRepositoryImplTest` 用真实 MyBatis-Plus + H2 或测试 MySQL,验证 update 返回的 KbRepository 持有 `version+1`
- **事务时序**:`KbRepositoryApplicationServiceTest` 用 `TransactionSynchronizationManager.registerSynchronization` 的 Mockito spy 验证 `executeAsync` 在 afterCommit 阶段调用

集成测试的复杂度评估:项目当前测试用 MockitoExtension(纯 Mock,无 SpringBootTest),要不要为本 change 引入 `@SpringBootTest` 或 `@MybatisPlusTest`?**决定**:重 mock 验证返回值即可(`when(mapper.updateById(any())).then(invocation -> { po.setVersion(po.getVersion()+1); return 1; })`),不引入集成测试基础设施。

## Risks / Trade-offs

- **[`update()` 签名变化破坏向后兼容]** → 仅返回类型增加,调用方可忽略,Java 编译器允许。✓ 实际向后兼容,无破坏
- **[`TransactionSynchronizationManager` 在非事务上下文调用会抛 IllegalStateException]** → `create()` 是 `@Transactional`,必然有事务。但单元测试场景如果没 mock TransactionSynchronizationManager,会抛。Mitigation:测试用 spy 或 `MockedStatic` 处理
- **[`mapper.updateById(po)` 抛 OptimisticLockingFailureException 时返回值未定义]** → 异常会向上传播,调用方根本拿不到返回值。无风险
- **[Assembler.toDomain(po) 之后 PO 字段顺序与 domain 不一致]** → 已有 #11 测试覆盖 Assembler 双向映射,本 change 不改 Assembler

## Migration Plan

1. **代码**:按 D1-D5 顺序逐文件修改,先 domain 接口 → infra 实现 → service 调用方
2. **测试**:跑 #11 现有测试套件,确认无回归;新增 3 个测试覆盖修复路径
3. **集成验证**:端到端重做"创建仓库 → clone 失败(用空 URL)→ 查 DB"流程,确认 `sync_status='ERROR'` 且 `last_error_message` 非空
4. **回滚**:revert commit 即可,无数据/schema 变更
