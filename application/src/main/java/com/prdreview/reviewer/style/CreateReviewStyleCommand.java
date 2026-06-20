package com.prdreview.reviewer.style;

import com.prdreview.reviewer.style.model.StyleRule;

import java.util.List;

/**
 * 创建评审风格命令对象。
 */
public record CreateReviewStyleCommand(
    String name,
    String icon,
    String scenario,
    List<StyleRule> rules,
    Integer sortOrder
) {
}
