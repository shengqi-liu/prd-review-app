package com.prdreview.ai.dto;

/**
 * AI 摘要结果：title（文档标题）+ content（核心内容摘要）。
 * <p>由 {@link com.prdreview.ai.service.AiService} 返回，供 change#5 PRD 存储及后续 change 复用。
 */
public record SummarizeResult(String title, String content) {
}
