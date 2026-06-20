package com.prdreview.reviewer;

/**
 * 试跑评审员命令对象（用一份临时 PRD 渲染评审员的 Prompt 模板）。
 */
public record TestReviewerCommand(
    Long reviewerId,
    String prdTitle,
    String prdContent
) {
}
