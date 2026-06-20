package com.prdreview.reviewer.style;

import java.util.List;

/**
 * 评审风格分页结果。
 */
public record ReviewStylePageResult(
    long total,
    List<ReviewStyleDTO> items
) {
}
