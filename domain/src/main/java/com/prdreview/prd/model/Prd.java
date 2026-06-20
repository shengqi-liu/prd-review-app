package com.prdreview.prd.model;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * PRD 聚合根（富血模型）。
 *
 * <p>纯 Java 对象，无 MyBatis 注解。状态机规则全部封装在此类内部。
 */
@Getter
public class Prd {

    private Long id;
    private String title;
    private String content;
    private String sourceUrl;
    private Long authorId;
    private PrdStatus status;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── 静态工厂 ─────────────────────────────────────────────────────

    /**
     * 手动路径：直接创建草稿。
     */
    public static Prd createFromManual(String title, String content, Long authorId) {
        Prd prd = new Prd();
        prd.title = title;
        prd.content = content;
        prd.authorId = authorId;
        prd.status = PrdStatus.DRAFT;
        prd.version = 1;
        return prd;
    }

    /**
     * URL 路径：创建 INITIALIZING 状态的占位 PRD，等待 AI 摘要填充 title/content。
     */
    public static Prd createFromUrl(String sourceUrl, Long authorId) {
        Prd prd = new Prd();
        prd.sourceUrl = sourceUrl;
        prd.authorId = authorId;
        prd.status = PrdStatus.INITIALIZING;
        prd.version = 1;
        return prd;
    }

    /**
     * 从持久化对象重建（供 PrdAssembler 使用）。
     */
    public static Prd reconstruct(Long id, String title, String content, String sourceUrl,
                                   Long authorId, PrdStatus status, Integer version,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
        Prd prd = new Prd();
        prd.id = id;
        prd.title = title;
        prd.content = content;
        prd.sourceUrl = sourceUrl;
        prd.authorId = authorId;
        prd.status = status;
        prd.version = version;
        prd.createdAt = createdAt;
        prd.updatedAt = updatedAt;
        return prd;
    }

    // ── 行为方法（状态机） ────────────────────────────────────────────

    /**
     * 提交评审：DRAFT → SUBMITTED。
     *
     * @throws BizException PRD_OPERATION_NOT_ALLOWED 若当前不是 DRAFT 状态
     */
    public void submit() {
        if (this.status != PrdStatus.DRAFT) {
            throw new BizException(ErrorCode.PRD_OPERATION_NOT_ALLOWED);
        }
        this.status = PrdStatus.SUBMITTED;
    }

    /**
     * 完成 AI 初始化：INITIALIZING → DRAFT，填充 title 和 content。
     *
     * @throws BizException PRD_OPERATION_NOT_ALLOWED 若当前不是 INITIALIZING 状态
     */
    public void completeInitialization(String title, String content) {
        if (this.status != PrdStatus.INITIALIZING) {
            throw new BizException(ErrorCode.PRD_OPERATION_NOT_ALLOWED);
        }
        this.title = title;
        this.content = content;
        this.status = PrdStatus.DRAFT;
    }

    /**
     * 开始评审（桩，change#17 实现）：SUBMITTED → UNDER_REVIEW。
     */
    public void startReview() {
        if (this.status != PrdStatus.SUBMITTED) {
            throw new BizException(ErrorCode.PRD_OPERATION_NOT_ALLOWED);
        }
        this.status = PrdStatus.UNDER_REVIEW;
    }

    /**
     * 通过评审（桩，change#20 实现）：UNDER_REVIEW → APPROVED。
     */
    public void approve() {
        if (this.status != PrdStatus.UNDER_REVIEW) {
            throw new BizException(ErrorCode.PRD_OPERATION_NOT_ALLOWED);
        }
        this.status = PrdStatus.APPROVED;
    }

    /**
     * 拒绝评审（桩，change#20 实现）：UNDER_REVIEW → REJECTED。
     */
    public void reject() {
        if (this.status != PrdStatus.UNDER_REVIEW) {
            throw new BizException(ErrorCode.PRD_OPERATION_NOT_ALLOWED);
        }
        this.status = PrdStatus.REJECTED;
    }

    /**
     * 更新草稿内容（仅 DRAFT 状态）。
     */
    public void updateDraft(String title, String content) {
        if (this.status != PrdStatus.DRAFT) {
            throw new BizException(ErrorCode.PRD_OPERATION_NOT_ALLOWED);
        }
        this.title = title;
        this.content = content;
    }

    // ── 查询方法 ─────────────────────────────────────────────────────

    /** 是否为本人所有 */
    public boolean isOwnedBy(Long userId) {
        return this.authorId != null && this.authorId.equals(userId);
    }

    /**
     * 是否对给定用户可见。
     * <ul>
     *   <li>ADMIN / TEAM_MEMBER：可见所有（含 INITIALIZING）</li>
     *   <li>SUBMITTER：仅可见自己的，且非 INITIALIZING 状态</li>
     * </ul>
     */
    public boolean isVisibleTo(Long userId, String role) {
        if ("ADMIN".equals(role) || "TEAM_MEMBER".equals(role)) {
            return true;
        }
        return isOwnedBy(userId) && this.status != PrdStatus.INITIALIZING;
    }

    /** 是否可编辑（仅 DRAFT 状态） */
    public boolean isEditable() {
        return this.status == PrdStatus.DRAFT;
    }

    /**
     * 是否可被指定用户删除。
     * <ul>
     *   <li>状态必须是 DRAFT 或 INITIALIZING</li>
     *   <li>且本人或 ADMIN</li>
     * </ul>
     */
    public boolean isDeletableBy(Long userId, String role) {
        boolean isDeletableStatus = this.status == PrdStatus.DRAFT
                || this.status == PrdStatus.INITIALIZING;
        return isDeletableStatus && (isOwnedBy(userId) || "ADMIN".equals(role));
    }
}
