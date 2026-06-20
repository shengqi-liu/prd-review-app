package com.prdreview.reviewer.repository;

import com.prdreview.reviewer.model.Reviewer;

import java.util.List;

/**
 * 评审员持久化接口（领域层定义，基础设施层实现）。
 */
public interface ReviewerRepository {

    /**
     * 按 ID 查询评审员（已软删除的不可见）。
     *
     * @return 评审员，不存在时返回 null
     */
    Reviewer findById(Long id);

    /**
     * 插入新评审员，返回带 id 的评审员。
     */
    Reviewer save(Reviewer reviewer);

    /**
     * 乐观锁更新评审员（version 字段自动递增）。
     * 影响行数为 0 时由 MyBatis-Plus 抛 OptimisticLockingFailureException。
     */
    void update(Reviewer reviewer);

    /**
     * 逻辑删除（SET deleted=1）。
     */
    void softDelete(Long id);

    /**
     * 分页条件查询。
     *
     * @param page    页码（从 1 开始）
     * @param size    每页条数
     * @param enabled 是否启用筛选；null 表示不过滤
     * @return 分页结果，按 sort_order ASC, id ASC 排序
     */
    ReviewerPage findPageByCondition(int page, int size, Boolean enabled);

    /**
     * 检查名称是否已存在（用于唯一性校验，排除指定 id 和已删除记录）。
     *
     * @param name      待检查的名称
     * @param excludeId 排除的评审员 id（更新时传入自身 id，创建时传 null）
     * @return true 表示存在同名记录
     */
    boolean existsByName(String name, Long excludeId);

    /**
     * 分页结果包装类。
     */
    record ReviewerPage(long total, List<Reviewer> items) {}
}
