package com.prdreview.prd;

/**
 * 手动创建 PRD 命令（Application 层输入）。
 */
public record CreatePrdCommand(String title, String content, Long authorId) {
}
