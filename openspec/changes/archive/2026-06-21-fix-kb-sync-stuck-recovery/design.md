## Context

#11 落地后,explore 阶段发现两个会让同步任务永久卡在 `SYNCING` 状态的漏洞——一个是配置项 `cloneTimeoutMs` 形同虚设(从未传给 JGit),一个是 JVM crash 后没有任何机制能把残留的 `SYNCING` 状态打回 `ERROR`。两者都属于"看似有保护,实际无防护"的可靠性盲区。

当前 `KbSyncTaskService.execute()` 流程:`markSyncing → update DB → clone/fetch → diff → publishEvent → markHealthy → update DB`。`markSyncing` 后任何环节挂起或进程崩溃,DB 状态就再也回不来。后续 `@Scheduled` 任务会因为"已是 SYNCING,跳过本轮"全部空转。

设计初稿曾考虑引入 watchdog 定时巡检,review 时认识到**过度设计**:单实例部署下,timeout(封堵 git hang)+ 启动清理(封堵 JVM crash 残留)已覆盖所有现实场景,而 watchdog 独自覆盖的"运行中代码 bug"概率极低且已有双层 catch 兜底。简化为本方案。

## Goals / Non-Goals

**Goals:**
- 任何网络/IO 异常下,git 操作必须在有限时间内返回(成功或抛 TransportException)
- 进程重启后,所有残留的 `SYNCING` 状态在启动瞬间被清理为 `ERROR`,不阻塞首次同步
- 修复对前端、API、数据库 schema 全透明,最小化变更面

**Non-Goals:**
- 不处理事件投递可靠性(#12 决策项)
- 不引入 watchdog 定时巡检(过度设计,见 Decisions D3)
- 不引入分布式锁(单实例够用)
- 不重试卡死的 git 操作本身(启动清理仅恢复状态,下次调度自然重试)
- 不加 schema 列(启动清理不依赖时间戳)

## Decisions

### D1:超时单位与配置分离

JGit `TransportCommand.setTimeout(int seconds)` 接受**秒**而非毫秒,需要在 `GitOperations` 内做单位换算 `(int)(timeoutMs / 1000)`,并对 `< 1000ms` 的配置兜底为 `1` 秒(防止 `setTimeout(0)`,JGit 视为无超时,反而是 bug)。

`clone` 和 `fetch` 用**独立**配置:
- `cloneTimeoutMs`(已存在,默认 300_000 = 5 分钟)——首次 clone 可能拉几百 MB,要给够
- `fetchTimeoutMs`(新增,默认 60_000 = 1 分钟)——增量 fetch 通常秒级,卡 1 分钟就该放弃

**备选**:统一一个 `gitOperationTimeoutMs` —— 简单但要么 clone 误杀(设短),要么 fetch hang 等太久(设长),折中无解。

### D2:启动清理用 `ApplicationRunner`,不用 `@PostConstruct`

新建 `KbSyncStartupCleanup implements ApplicationRunner`,在 Spring 完成所有 Bean 初始化、且 ApplicationContext refresh 完成后被 Spring 调用一次。

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class KbSyncStartupCleanup implements ApplicationRunner {
    private final KbRepositoryRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        List<KbRepository> stuck = repository.findAllSyncing();
        for (KbRepository repo : stuck) {
            repo.markError("startup cleanup: stale SYNCING from previous shutdown/crash");
            repository.update(repo);
            log.warn("[KB-Startup] cleared stale SYNCING id={} name={}",
                repo.getId(), repo.getName());
        }
        if (!stuck.isEmpty()) {
            log.info("[KB-Startup] cleaned {} stale SYNCING repository(s)", stuck.size());
        }
    }
}
```

**为什么不用 `@PostConstruct`?** `@PostConstruct` 在单个 Bean 实例化完成时就触发,可能早于 DataSource/MyBatis-Plus 完全就绪;`ApplicationRunner` 在 Spring 上下文完全就绪后才被调用,DB 操作必然安全。

**为什么不用 `ApplicationListener<ContextRefreshedEvent>`?** ApplicationRunner 语义更明确(它就是为"启动后跑一次"设计的),且不会被父子上下文重复触发。

### D3:为什么不要 watchdog(放弃的方案,记录原因)

初稿曾设计 watchdog `@Scheduled` 每 5 分钟扫描超过 15 分钟的 SYNCING。review 时把"watchdog 解决什么问题"列开:

| 场景 | timeout(D1) | 启动清理(D2) | watchdog |
|------|-------------|-------------|----------|
| 网络慢 git hang | ✓ 抛 TransportException | ✓ | ✓ |
| JVM crash → 重启 | ✗ | ✓ 启动瞬间清理 | ✓ 15min 后 |
| JVM 不重启、git 真的卡 | ✓ timeout 救 | — | ✓ |
| JVM 不重启、代码 bug 漏 markError | ✗ | ✗ | ✓ |
| 多实例 | ✗ | ✗(只清自己重启) | ✓ |

watchdog 独家覆盖的只有"运行中代码 bug 漏 markError"和"多实例"。项目当前**单实例**(已记入 #11 D5),多实例不考虑;代码 bug 场景由 `KbSyncTaskService` 的双层 `catch (BizException)` + `catch (Exception)` 兜底,要让其失效需要 Spring 框架级 bug,概率极低。

工程量对比:
- watchdog:V7 迁移加列 + domain 加 `statusChangedAt` + 改 3 个 markXxx + 改 reconstruct/create + PO/Assembler 改 + 新增 repository 方法 + 新增 watchdog 类 + 2 条 spec + 多套测试 = **约 25 个改动点**
- 启动清理:新增 1 个 ApplicationRunner 类 + 1 个 repository 方法 + 1 条 spec = **约 3 个改动点**

差距 8 倍。删除 watchdog 是正确的简化。

### D4:启动清理的并发安全

应用启动是单进程单线程时刻,`KbSyncStartupCleanup.run()` 与 `@Scheduled` 任务的并发关系:

- `KbSyncScheduler` 也是 `@Scheduled`,Spring 默认在 `ApplicationContext` 完全就绪后才开始调度
- `ApplicationRunner.run()` 在 Spring 完成所有 ApplicationListener 后被调用,先于第一次 `@Scheduled` 触发
- 即便存在并发(理论上可能),`markError` 通过乐观锁(`@Version`)保证数据一致性——其中一方失败不影响功能

**结论**:启动清理几乎不可能与同步任务并发,无需特殊同步控制。

### D5:`findAllSyncing` 接口设计

在 `KbRepositoryRepository` 接口新增:
```java
List<KbRepository> findAllSyncing();
```

实现:`LambdaQueryWrapper.eq(syncStatus, "SYNCING")` → 转聚合根列表。当前单仓库场景必然返回 0 或 1 个元素,用 `List` 是为未来多仓库做准备(几乎零成本)。

## Risks / Trade-offs

- **[启动清理只能恢复重启场景]** → 这是设计选择。如果 JVM 不重启但 git 卡死(虽然 timeout 应已封堵),则状态会停留在 SYNCING 直到下次 JVM 重启或运维介入。Mitigation:timeout 已经覆盖 git hang;真出现"timeout 失效"的极端场景,作为已知限制接受。
- **[启动清理在 DB 不可用时会失败]** → `ApplicationRunner` 抛错会导致应用启动失败。这是 fail-fast 行为,反而是好事(DB 都不通,继续启动也没意义)。无需特殊处理。
- **[多实例部署时的并发清理]** → 不在本 change 范围(`#11 D5` 已假设单实例)。多实例场景:每个实例启动都会执行清理,通过乐观锁保证只有一个成功,其他失败无害。无功能 bug。

## Migration Plan

1. **代码**:逐层修改 domain → infrastructure → application → 配置,不破坏现有 API/事件契约
2. **数据库**:无 schema 变更
3. **回滚**:移除 `KbSyncStartupCleanup` Bean 与 `findAllSyncing` 方法即可,无数据残留
4. **验证**:
   - 单元测试:所有 #11 现有测试继续通过
   - 集成 1:手动 `UPDATE kb_repository SET sync_status='SYNCING' WHERE id=1;` → 重启应用 → 启动日志含 "cleared stale SYNCING" → 查询确认 `sync_status='ERROR'`,`last_error_message` 含 "startup cleanup"
   - 集成 2:启动后 1 小时内(默认轮询)触发首次同步 → 正常 fetch 转回 HEALTHY
