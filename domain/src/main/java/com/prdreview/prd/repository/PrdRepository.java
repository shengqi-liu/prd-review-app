package com.prdreview.prd.repository;

import com.prdreview.prd.model.Prd;

import java.util.List;

/**
 * PRD 持久化接口（领域层定义，基础设施层实现）。
 */
public interface PrdRepository {

    /**
     * 按 ID 查询 PRD（已软删除的不可见）。
     *
     * @return PRD，不存在时返回 null
     */
    Prd findById(Long id);

    /**
     * 插入新 PRD，返回带 id 的 PRD。
     */
    Prd save(Prd prd);

    /**
     * 乐观锁更新 PRD（version 字段自动递增）。
     * 影响行数为 0 时由 MyBatis-Plus 抛 OptimisticLockingFailureException。
     */
    void update(Prd prd);

    /**
     * 逻辑删除（SET deleted=1）。
     */
    void softDelete(Long id);

    /**
     * 分页条件查询。
     *
     * @param page            页码（从 1 开始）
     * @param size            每页条数
     * @param authorId        SUBMITTER 角色时过滤条件；ADMIN/TEAM_MEMBER 传 null 不过滤
     * @param excludeInitializing 是否排除 INITIALIZING 状态（列表接口传 true）
     * @return 分页结果
     */
    PrdPage findPageByCondition(int page, int size, Long authorId, boolean excludeInitializing);

    /**
     * 分页结果包装类。
     */
    record PrdPage(long total, List<Prd> items) {}
}
