package com.prdreview.reviewer.style.model;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评审风格聚合根（富血模型）。
 *
 * <p>纯 Java 对象，无 MyBatis 注解。规则数量约束与默认风格保护封装在内部。
 *
 * <p>核心不变量：
 * <ul>
 *   <li>rules 数量在 {@value MIN_RULES}–{@value MAX_RULES} 条之间</li>
 *   <li>每条规则的 label 和 content 非空</li>
 *   <li>默认风格（isDefault=true）不可禁用、不可删除</li>
 * </ul>
 */
@Getter
public class ReviewStyle {

    public static final int MIN_RULES = 4;
    public static final int MAX_RULES = 8;

    private Long id;
    private String name;
    private String icon;
    private String scenario;
    private List<StyleRule> rules;
    private Boolean enabled;
    private Boolean isDefault;
    private Integer sortOrder;
    private Integer version;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── 静态工厂 ─────────────────────────────────────────────────────

    /**
     * 创建评审风格：默认启用、非默认、版本 1。
     *
     * <p>无论入参是否要求设置为默认，都强制 isDefault=false——切换默认风格必须走专用入口。
     */
    public static ReviewStyle create(String name, String icon, String scenario,
                                     List<StyleRule> rules, Integer sortOrder) {
        ReviewStyle s = new ReviewStyle();
        s.name = name;
        s.icon = icon;
        s.scenario = scenario;
        s.rules = rules;
        s.enabled = Boolean.TRUE;
        s.isDefault = Boolean.FALSE;
        s.sortOrder = sortOrder != null ? sortOrder : 0;
        s.version = 1;
        s.deleted = 0;
        s.validateRules();
        return s;
    }

    /**
     * 从持久化对象重建（供 ReviewStyleAssembler 使用）。
     */
    public static ReviewStyle reconstruct(Long id, String name, String icon, String scenario,
                                          List<StyleRule> rules, Boolean enabled, Boolean isDefault,
                                          Integer sortOrder, Integer version, Integer deleted,
                                          LocalDateTime createdAt, LocalDateTime updatedAt) {
        ReviewStyle s = new ReviewStyle();
        s.id = id;
        s.name = name;
        s.icon = icon;
        s.scenario = scenario;
        s.rules = rules;
        s.enabled = enabled;
        s.isDefault = isDefault;
        s.sortOrder = sortOrder;
        s.version = version;
        s.deleted = deleted;
        s.createdAt = createdAt;
        s.updatedAt = updatedAt;
        return s;
    }

    // ── 领域行为 ─────────────────────────────────────────────────────

    /**
     * 更新评审风格（不含 isDefault；切换默认走专用入口）。
     *
     * <p>触发 {@link #validateRules()}；若聚合根为默认风格且 enabled 入参为 false，抛
     * {@link ErrorCode#STYLE_DEFAULT_NOT_DISABLABLE}。
     */
    public void update(String name, String icon, String scenario, List<StyleRule> rules,
                       Boolean enabled, Integer sortOrder) {
        Boolean newEnabled = enabled != null ? enabled : Boolean.TRUE;
        if (Boolean.TRUE.equals(this.isDefault) && Boolean.FALSE.equals(newEnabled)) {
            throw new BizException(ErrorCode.STYLE_DEFAULT_NOT_DISABLABLE);
        }
        this.name = name;
        this.icon = icon;
        this.scenario = scenario;
        this.rules = rules;
        this.enabled = newEnabled;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.validateRules();
    }

    /**
     * 逻辑删除标记。
     *
     * <p>默认风格不可删除；实际写库由 Repository 通过 MyBatis-Plus {@code @TableLogic} 完成，
     * 此方法仅在内存中校验并调整聚合根状态。
     */
    public void markDeleted() {
        if (Boolean.TRUE.equals(this.isDefault)) {
            throw new BizException(ErrorCode.STYLE_DEFAULT_NOT_DELETABLE);
        }
        this.deleted = 1;
    }

    /**
     * 将本风格标记为默认。仅供 Application 层在 set-default 事务内调用。
     */
    public void markAsDefault() {
        this.isDefault = Boolean.TRUE;
    }

    /**
     * 清除默认标记。仅供 Application 层在 set-default 事务内调用。
     */
    public void unmarkDefault() {
        this.isDefault = Boolean.FALSE;
    }

    /**
     * 规则列表合法性校验。
     *
     * <ul>
     *   <li>数量必须在 {@value MIN_RULES}–{@value MAX_RULES} 条之间（含两端）</li>
     *   <li>每条规则的 label 和 content 非空字符串</li>
     * </ul>
     */
    public void validateRules() {
        if (this.rules == null || this.rules.size() < MIN_RULES || this.rules.size() > MAX_RULES) {
            throw new BizException(
                ErrorCode.STYLE_RULE_INVALID,
                "规则数量必须在 " + MIN_RULES + "–" + MAX_RULES + " 条之间"
            );
        }
        for (int i = 0; i < this.rules.size(); i++) {
            StyleRule r = this.rules.get(i);
            if (r == null || r.label() == null || r.label().isBlank()
                || r.content() == null || r.content().isBlank()) {
                throw new BizException(
                    ErrorCode.STYLE_RULE_INVALID,
                    "第 " + (i + 1) + " 条规则的 label 或 content 不能为空"
                );
            }
        }
    }
}
