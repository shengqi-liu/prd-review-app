package com.prdreview.reviewer;

/**
 * 更新评审员命令对象。
 */
public record UpdateReviewerCommand(
    Long reviewerId,
    String name,
    String icon,
    String description,
    String promptTemplate,
    Boolean enabled,
    Integer sortOrder,
    Integer version
) {
}
