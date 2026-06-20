package com.prdreview.prd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 更新 PRD 草稿请求 DTO。
 *
 * <p>{@code version} 为乐观锁版本号，前端必须携带上次读取到的值。
 */
public record UpdatePrdRequest(
    @NotBlank(message = "title 不能为空") String title,
    @NotBlank(message = "content 不能为空") String content,
    @NotNull(message = "version 不能为空") Integer version
) {
}
