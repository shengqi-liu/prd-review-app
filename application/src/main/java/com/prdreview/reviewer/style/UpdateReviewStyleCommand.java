package com.prdreview.reviewer.style;

import com.prdreview.reviewer.style.model.StyleRule;

import java.util.List;

/**
 * 更新评审风格命令对象。
 *
 * <p>不含 isDefault — 切换默认走 setDefault() 专用入口。
 */
public record UpdateReviewStyleCommand(
    Long styleId,
    String name,
    String icon,
    String scenario,
    List<StyleRule> rules,
    Boolean enabled,
    Integer sortOrder,
    Integer version
) {
}
