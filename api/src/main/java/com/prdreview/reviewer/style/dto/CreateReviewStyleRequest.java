package com.prdreview.reviewer.style.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建评审风格请求 DTO。
 */
public record CreateReviewStyleRequest(
    @NotBlank(message = "name 不能为空")
    @Size(max = 50, message = "name 长度不能超过 50")
    String name,

    @Size(max = 20, message = "icon 长度不能超过 20")
    String icon,

    @Size(max = 200, message = "scenario 长度不能超过 200")
    String scenario,

    @Valid
    @NotEmpty(message = "rules 不能为空")
    @Size(min = 4, max = 8, message = "rules 数量必须在 4–8 条之间")
    List<StyleRuleRequest> rules,

    Integer sortOrder
) {
}
