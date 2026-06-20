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

    /** 是否已存在未删除仓库（用于单仓库约束校验）。 */
    boolean existsActive();

    /** 插入新仓库，返回带 id 的对象。 */
    KbRepository save(KbRepository repository);

    /**
     * 乐观锁更新（version 字段自动递增）。
     * 影响行数为 0 时由 MyBatis-Plus 抛 OptimisticLockingFailureException。
     */
    void update(KbRepository repository);

    /** 逻辑删除（SET deleted=1）。 */
    void softDelete(Long id);
}
