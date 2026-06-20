package com.prdreview.prd;

import java.util.List;

/**
 * PRD 分页查询结果。
 */
public record PrdPageResult(long total, List<PrdDTO> items) {
}
