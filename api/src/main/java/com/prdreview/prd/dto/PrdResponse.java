package com.prdreview.prd.dto;

import com.prdreview.prd.PrdDTO;

import java.time.LocalDateTime;

/**
 * PRD HTTP 响应 DTO。
 */
public record PrdResponse(
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
    public static PrdResponse from(PrdDTO dto) {
        return new PrdResponse(
            dto.id(), dto.title(), dto.content(), dto.sourceUrl(),
            dto.authorId(), dto.status(), dto.version(),
            dto.createdAt(), dto.updatedAt()
        );
    }
}
