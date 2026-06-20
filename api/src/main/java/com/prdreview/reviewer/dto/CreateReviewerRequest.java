package com.prdreview.reviewer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建评审员请求 DTO。
 */
public record CreateReviewerRequest(
    @NotBlank(message = "name 不能为空")
    @Size(max = 100, message = "name 长度不能超过 100")
    String name,

    @Size(max = 20, message = "icon 长度不能超过 20")
    String icon,

    @Size(max = 500, message = "description 长度不能超过 500")
    String description,

    @NotBlank(message = "promptTemplate 不能为空")
    String promptTemplate
) {
}
