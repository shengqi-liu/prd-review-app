package com.prdreview.reviewer.style.dto;

import com.prdreview.reviewer.style.StyleRuleDTO;

/**
 * 评审风格规则响应 DTO。
 */
public record StyleRuleResponse(String label, String content) {

    public static StyleRuleResponse from(StyleRuleDTO dto) {
        return new StyleRuleResponse(dto.label(), dto.content());
    }
}
