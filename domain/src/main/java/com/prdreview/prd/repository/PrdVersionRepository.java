package com.prdreview.prd.repository;

import com.prdreview.prd.model.PrdVersion;

import java.util.List;

/**
 * PRD 版本快照持久化接口。
 */
public interface PrdVersionRepository {

    /**
     * 保存版本快照。
     */
    void save(PrdVersion prdVersion);

    /**
     * 查询指定 PRD 的所有版本快照，按 version ASC 排序。
     */
    List<PrdVersion> findByPrdId(Long prdId);
}
