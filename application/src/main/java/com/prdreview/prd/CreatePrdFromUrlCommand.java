package com.prdreview.prd;

/**
 * 从 URL 创建 PRD 命令（Application 层输入）。
 */
public record CreatePrdFromUrlCommand(String sourceUrl, Long authorId) {
}
