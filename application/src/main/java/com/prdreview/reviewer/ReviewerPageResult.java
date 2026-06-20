package com.prdreview.reviewer;

import java.util.List;

/**
 * 评审员分页结果。
 */
public record ReviewerPageResult(
    long total,
    List<ReviewerDTO> items
) {
}
