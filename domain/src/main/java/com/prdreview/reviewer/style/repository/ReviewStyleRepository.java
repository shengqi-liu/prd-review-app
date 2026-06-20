package com.prdreview.reviewer.style.repository;

import com.prdreview.reviewer.style.model.ReviewStyle;

import java.util.List;

/**
 * 评审风格持久化接口（领域层定义，基础设施层实现）。
 */
public interface ReviewStyleRepository {

    /**
     * 按 ID 查询评审风格（已软删除的不可见）。
     *
     * @return 评审风格，不存在时返回 null
     */
    ReviewStyle findById(Long id);

    /**
     * 查询当前默认风格（is_default=1 且 enabled=1 且未删除）。
     */
    ReviewStyle findDefault();

    /**
     * 查询所有启用的风格（按 sort_order ASC, id ASC）。
     */
    List<ReviewStyle> findAllEnabled();

    /**
     * 插入新评审风格，返回带 id 的对象。
     */
    ReviewStyle save(ReviewStyle style);

    /**
     * 乐观锁更新（version 字段自动递增）。
     * 影响行数为 0 时由 MyBatis-Plus 抛 OptimisticLockingFailureException。
     */
    void update(ReviewStyle style);

    /**
     * 逻辑删除（SET deleted=1）。
     */
    void softDelete(Long id);

    /**
     * 分页条件查询。
     *
     * @param page    页码（从 1 开始）
     * @param size    每页条数
     * @param enabled 启用筛选；null 表示不过滤
     * @return 分页结果，按 sort_order ASC, id ASC 排序
     */
    ReviewStylePage findPageByCondition(int page, int size, Boolean enabled);

    /**
     * 名称唯一性检查（排除指定 id 和已删除记录）。
     *
     * @param name      待检查的名称
     * @param excludeId 排除的风格 id（更新时传入自身 id，创建时传 null）
     * @return true 表示存在同名记录
     */
    boolean existsByName(String name, Long excludeId);

    /**
     * 一次性清除所有风格的默认标记（is_default=1 → 0），仅用于 set-default 事务内。
     */
    void clearAllDefaultFlags();

    /**
     * 分页结果包装类。
     */
    record ReviewStylePage(long total, List<ReviewStyle> items) {}
}
