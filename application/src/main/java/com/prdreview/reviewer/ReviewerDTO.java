package com.prdreview.reviewer;

import java.time.LocalDateTime;

/**
 * 评审员 Application 层输出 DTO。
 */
public record ReviewerDTO(
    Long id,
    String name,
    String icon,
    String description,
    String promptTemplate,
    Boolean enabled,
    Integer sortOrder,
    Integer version,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
