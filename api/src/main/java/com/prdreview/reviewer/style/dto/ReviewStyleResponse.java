package com.prdreview.reviewer.style.dto;

import com.prdreview.reviewer.style.ReviewStyleDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评审风格 HTTP 响应 DTO。
 */
public record ReviewStyleResponse(
    Long id,
    String name,
    String icon,
    String scenario,
    List<StyleRuleResponse> rules,
    Boolean enabled,
    Boolean isDefault,
    Integer sortOrder,
    Integer version,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static ReviewStyleResponse from(ReviewStyleDTO dto) {
        List<StyleRuleResponse> rules = dto.rules() == null
            ? List.of()
            : dto.rules().stream().map(StyleRuleResponse::from).toList();
        return new ReviewStyleResponse(
            dto.id(), dto.name(), dto.icon(), dto.scenario(), rules,
            dto.enabled(), dto.isDefault(), dto.sortOrder(), dto.version(),
            dto.createdAt(), dto.updatedAt()
        );
    }
}
