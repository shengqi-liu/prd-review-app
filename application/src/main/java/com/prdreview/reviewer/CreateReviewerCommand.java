package com.prdreview.reviewer;

/**
 * 创建评审员命令对象。
 */
public record CreateReviewerCommand(
    String name,
    String icon,
    String description,
    String promptTemplate
) {
}
