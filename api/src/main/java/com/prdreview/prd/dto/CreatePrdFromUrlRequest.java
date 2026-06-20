package com.prdreview.prd.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

/**
 * 从 URL 创建 PRD 请求 DTO（SSE 路径）。
 */
public record CreatePrdFromUrlRequest(
    @NotBlank(message = "sourceUrl 不能为空")
    @URL(message = "sourceUrl 格式不合法")
    String sourceUrl
) {
}
