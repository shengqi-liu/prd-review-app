package com.prdreview.prd.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 手动创建 PRD 请求 DTO。
 */
public record CreatePrdRequest(
    @NotBlank(message = "title 不能为空") String title,
    @NotBlank(message = "content 不能为空") String content
) {
}
