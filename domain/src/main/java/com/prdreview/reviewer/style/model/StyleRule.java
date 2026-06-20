package com.prdreview.reviewer.style.model;

/**
 * 评审风格规则（不可变值对象）。
 *
 * <p>一条规则由 label（短标签）+ content（规则描述）组成；多条规则共同定义一种风格。
 */
public record StyleRule(String label, String content) {
}
