package com.prdreview.prd.validation;

import java.util.Locale;
import java.util.Set;

/**
 * PRD 必须包含的核心章节及其同义词关键词。
 *
 * <p>PRD 提交评审前必须含其中至少 {@link PrdInputValidator#MIN_REQUIRED_SECTIONS} 个章节。
 *
 * <p>匹配策略:**substring 包含匹配**(不区分大小写)——只要标题文本包含任一关键词就识别成功。
 * 例如 "一、需求背景" 含 "背景" → 命中 BACKGROUND;"二、目标用户" 含 "目标" → 命中 GOAL;
 * "三、功能设计" 含 "功能" 或 "设计" → 命中 DESIGN。这样支持中文序号前缀、修饰词等灵活写法。
 *
 * <p>关键词集合全部为**小写**形式,匹配时对入参 `trim().toLowerCase(Locale.ROOT)` 后比对。
 */
public enum RequiredSection {

    BACKGROUND("背景", Set.of(
        // 中文
        "背景", "概述", "简介", "概况", "由来", "缘起", "现状", "痛点", "问题",
        "需求来源", "项目背景", "立项",
        // 英文
        "background", "overview", "context", "introduction", "intro",
        "motivation", "problem"
    )),

    GOAL("目标", Set.of(
        // 中文
        "目标", "目的", "价值", "收益", "预期", "指标", "kpi", "okr",
        "意义", "愿景", "效果",
        // 英文
        "goal", "goals", "objective", "objectives", "purpose", "vision",
        "outcome", "metric", "metrics"
    )),

    DESIGN("功能设计", Set.of(
        // 中文
        "功能", "设计", "方案", "实现", "流程", "需求", "详细", "规范",
        "交互", "逻辑", "结构", "架构", "组成", "细节",
        // 英文
        "design", "solution", "feature", "features", "implementation",
        "flow", "specification", "spec", "requirement", "requirements",
        "architecture", "module"
    ));

    private final String displayName;
    private final Set<String> aliases;

    RequiredSection(String displayName, Set<String> aliases) {
        this.displayName = displayName;
        this.aliases = aliases;
    }

    /** 用户可读的中文显示名（用于错误消息） */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 给定一个标题文本，判断是否匹配本章节的任一关键词(substring 包含匹配)。
     *
     * <p>例如 "一、需求背景" 含关键词 "背景",视为命中 BACKGROUND;
     * "功能设计详述" 含 "功能"/"设计",视为命中 DESIGN。
     *
     * @param heading 标题原文（允许首尾空白、混合大小写、null）
     */
    public boolean matches(String heading) {
        if (heading == null) return false;
        String normalized = heading.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return false;
        for (String alias : aliases) {
            if (normalized.contains(alias)) return true;
        }
        return false;
    }
}
