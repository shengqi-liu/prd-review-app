package com.prdreview.reviewer.model;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 评审员聚合根（富血模型）。
 *
 * <p>纯 Java 对象，无 MyBatis 注解。
 *
 * <p>{@code promptTemplate} 是评审员的「角色定义」——纯 system prompt，
 * 描述评审员是谁、从什么视角评审、关注哪些维度。被评审的 PRD 不在此模板内，
 * 而是由编排层（试跑 #9 / Prompt Composer #15）作为独立的 user 消息附加。
 * 这样评审员是固定的，PRD 是变量，两者彻底解耦。
 */
@Getter
public class Reviewer {

    private Long id;
    private String name;
    private String icon;
    private String description;
    private String promptTemplate;
    private Boolean enabled;
    private Integer sortOrder;
    private Integer version;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── 静态工厂 ─────────────────────────────────────────────────────

    /**
     * 创建评审员：默认启用、排序权重 0、版本 1。
     */
    public static Reviewer create(String name, String icon, String description, String promptTemplate) {
        Reviewer r = new Reviewer();
        r.name = name;
        r.icon = icon;
        r.description = description;
        r.promptTemplate = promptTemplate;
        r.enabled = Boolean.TRUE;
        r.sortOrder = 0;
        r.version = 1;
        r.deleted = 0;
        r.validatePromptTemplate();
        return r;
    }

    /**
     * 从持久化对象重建（供 ReviewerAssembler 使用）。
     */
    public static Reviewer reconstruct(Long id, String name, String icon, String description,
                                       String promptTemplate, Boolean enabled, Integer sortOrder,
                                       Integer version, Integer deleted,
                                       LocalDateTime createdAt, LocalDateTime updatedAt) {
        Reviewer r = new Reviewer();
        r.id = id;
        r.name = name;
        r.icon = icon;
        r.description = description;
        r.promptTemplate = promptTemplate;
        r.enabled = enabled;
        r.sortOrder = sortOrder;
        r.version = version;
        r.deleted = deleted;
        r.createdAt = createdAt;
        r.updatedAt = updatedAt;
        return r;
    }

    // ── 领域行为 ─────────────────────────────────────────────────────

    /**
     * 更新评审员（除 id/version/createdAt 外的所有可变字段）。
     *
     * <p>内部触发 promptTemplate 合法性校验。
     */
    public void update(String name, String icon, String description, String promptTemplate,
                       Boolean enabled, Integer sortOrder) {
        this.name = name;
        this.icon = icon;
        this.description = description;
        this.promptTemplate = promptTemplate;
        this.enabled = enabled != null ? enabled : Boolean.TRUE;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.validatePromptTemplate();
    }

    /**
     * 逻辑删除标记。
     *
     * <p>实际写库由 Repository 通过 MyBatis-Plus {@code @TableLogic} 完成；
     * 此方法仅在内存中调整聚合根状态，便于测试与未来扩展。
     */
    public void markDeleted() {
        this.deleted = 1;
    }

    /**
     * Prompt 模板（system prompt）合法性校验。
     *
     * <p>模板是评审员的角色定义，只要求非空即可——不再包含/校验任何 PRD 占位符，
     * 被评审的 PRD 由编排层作为独立 user 消息附加。
     */
    public void validatePromptTemplate() {
        if (this.promptTemplate == null || this.promptTemplate.isBlank()) {
            throw new BizException(ErrorCode.REVIEWER_PROMPT_INVALID, "Prompt 模板不能为空");
        }
    }
}
