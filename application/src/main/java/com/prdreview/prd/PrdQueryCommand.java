package com.prdreview.prd;

/**
 * PRD 分页查询命令。
 */
public record PrdQueryCommand(int page, int size, Long currentUserId, String currentUserRole) {
}
