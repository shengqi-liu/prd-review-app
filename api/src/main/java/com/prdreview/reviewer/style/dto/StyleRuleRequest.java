package com.prdreview.reviewer.style.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 评审风格规则请求 DTO。
 */
public record StyleRuleRequest(
    @NotBlank(message = "rule.label 不能为空")
    @Size(max = 50, message = "rule.label 长度不能超过 50")
    String label,

    @NotBlank(message = "rule.content 不能为空")
    @Size(max = 500, message = "rule.content 长度不能超过 500")
    String content
) {
}
