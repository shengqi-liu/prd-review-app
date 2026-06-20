package com.prdreview.reviewer.style;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评审风格 Application 层输出 DTO。
 */
public record ReviewStyleDTO(
    Long id,
    String name,
    String icon,
    String scenario,
    List<StyleRuleDTO> rules,
    Boolean enabled,
    Boolean isDefault,
    Integer sortOrder,
    Integer version,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
