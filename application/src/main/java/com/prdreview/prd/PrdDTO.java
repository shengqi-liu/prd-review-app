package com.prdreview.prd;

import java.time.LocalDateTime;

/**
 * PRD Application 层输出 DTO。
 */
public record PrdDTO(
    Long id,
    String title,
    String content,
    String sourceUrl,
    Long authorId,
    String status,
    Integer version,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
