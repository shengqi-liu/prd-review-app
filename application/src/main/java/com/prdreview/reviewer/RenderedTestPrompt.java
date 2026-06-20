package com.prdreview.reviewer;

/**
 * 试跑渲染结果：分离的 system / user 两段消息。
 *
 * <p>{@code system} 是评审员的角色定义（评审员模板原文），
 * {@code user} 是被评审的 PRD（由 title + content 格式化而成）。
 * 评审员固定、PRD 变量，两者在此彻底解耦。
 */
public record RenderedTestPrompt(
    String system,
    String user
) {
}
