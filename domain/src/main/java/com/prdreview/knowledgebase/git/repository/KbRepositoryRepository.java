package com.prdreview.knowledgebase.git.repository;

import com.prdreview.knowledgebase.git.model.KbRepository;

import java.util.List;

/**
 * 知识库仓库持久化接口（领域层定义，基础设施层实现）。
 */
public interface KbRepositoryRepository {

    /** 按 ID 查询；已软删除返回 null。 */
    KbRepository findById(Long id);

    /** 返回唯一一个未删除仓库（系统约束至多 1 个）；不存在返回 null。 */
    KbRepository findActive();

    /** 列表查询（仅未删除，按 created_at ASC）。 */
    List<KbRepository> findAll();

    /**
     * 查询所有 {@code syncStatus = SYNCING} 的仓库（仅未删除）。
     *
     * <p>供启动清理（{@code KbSyncStartupCleanup}）回收上次进程残留的卡死状态。
     * 当前单仓库场景必然返回 0 或 1 条，用 List 是为未来多仓库做准备。
     */
    List<KbRepository> findAllSyncing();

    /** 是否已存在未删除仓库（用于单仓库约束校验）。 */
    boolean existsActive();

    /** 插入新仓库，返回带 id 的对象。 */
    KbRepository save(KbRepository repository);

    /**
     * 乐观锁更新（version 字段自动递增）。
     * 影响行数为 0 时由 MyBatis-Plus 抛 OptimisticLockingFailureException。
     *
     * <p><b>返回值</b>：带最新 version 的 domain 对象。调用方在同一执行流内若需要再次
     * update 同一聚合根，<b>必须使用返回值替换本地引用</b>——否则下次 update 的 WHERE version=?
     * 仍是旧值，与 DB 已自增的 version 不匹配，update 会 0 行静默失败。
     *
     * <p>典型用法：
     * <pre>
     * repo.markSyncing();
     * repo = repository.update(repo);   // 必须接收返回值
     * try { ... } catch (Exception e) {
     *     repo.markError(e.getMessage());
     *     repository.update(repo);      // 此处 update 终态，无需再接收
     * }
     * </pre>
     */
    KbRepository update(KbRepository repository);

    /** 逻辑删除（SET deleted=1）。 */
    void softDelete(Long id);
}
