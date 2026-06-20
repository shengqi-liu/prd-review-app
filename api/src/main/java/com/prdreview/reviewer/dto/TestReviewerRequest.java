package com.prdreview.reviewer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 试跑评审员请求 DTO。
 */
public record TestReviewerRequest(
    @NotBlank(message = "prdTitle 不能为空")
    @Size(max = 200, message = "prdTitle 长度不能超过 200")
    String prdTitle,

    @NotBlank(message = "prdContent 不能为空")
    @Size(max = 50000, message = "prdContent 长度不能超过 50000")
    String prdContent
) {
}
