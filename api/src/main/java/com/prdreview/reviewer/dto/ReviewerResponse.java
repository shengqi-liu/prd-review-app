package com.prdreview.reviewer.dto;

import com.prdreview.reviewer.ReviewerDTO;

import java.time.LocalDateTime;

/**
 * 评审员 HTTP 响应 DTO。
 */
public record ReviewerResponse(
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
    public static ReviewerResponse from(ReviewerDTO dto) {
        return new ReviewerResponse(
            dto.id(), dto.name(), dto.icon(), dto.description(), dto.promptTemplate(),
            dto.enabled(), dto.sortOrder(), dto.version(),
            dto.createdAt(), dto.updatedAt()
        );
    }
}
