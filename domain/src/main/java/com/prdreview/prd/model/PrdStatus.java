package com.prdreview.prd.model;

/**
 * PRD 状态枚举。
 *
 * <p>状态流转：
 * <pre>
 * INITIALIZING ──completeInitialization()──▶ DRAFT
 * DRAFT        ──submit()──────────────────▶ SUBMITTED
 * SUBMITTED    ──startReview()─────────────▶ UNDER_REVIEW
 * UNDER_REVIEW ──approve()─────────────────▶ APPROVED
 * UNDER_REVIEW ──reject()──────────────────▶ REJECTED
 * </pre>
 */
public enum PrdStatus {

    /** URL 路径创建后、AI 摘要完成前的中间状态 */
    INITIALIZING,

    /** 草稿（可编辑、可删除、可提交） */
    DRAFT,

    /** 已提交评审（等待评审员处理） */
    SUBMITTED,

    /** 评审中 */
    UNDER_REVIEW,

    /** 已通过 */
    APPROVED,

    /** 未通过 */
    REJECTED
}
