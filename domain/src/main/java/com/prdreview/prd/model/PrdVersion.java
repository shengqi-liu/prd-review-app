package com.prdreview.prd.model;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * PRD 版本快照实体（纯 Java 对象，无 MyBatis 注解）。
 *
 * <p>在 {@code submitPrd()} 事务内创建，记录提交时的 PRD 内容快照。
 */
@Getter
public class PrdVersion {

    private Long id;
    private Long prdId;
    private Integer version;
    private String title;
    private String content;
    private String sourceUrl;
    private LocalDateTime createdAt;

    public static PrdVersion create(Long prdId, Integer version, String title,
                                     String content, String sourceUrl) {
        PrdVersion pv = new PrdVersion();
        pv.prdId = prdId;
        pv.version = version;
        pv.title = title;
        pv.content = content;
        pv.sourceUrl = sourceUrl;
        return pv;
    }

    public static PrdVersion reconstruct(Long id, Long prdId, Integer version, String title,
                                          String content, String sourceUrl, LocalDateTime createdAt) {
        PrdVersion pv = new PrdVersion();
        pv.id = id;
        pv.prdId = prdId;
        pv.version = version;
        pv.title = title;
        pv.content = content;
        pv.sourceUrl = sourceUrl;
        pv.createdAt = createdAt;
        return pv;
    }
}
