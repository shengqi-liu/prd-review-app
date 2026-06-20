package com.prdreview.reviewer.style;

/**
 * 评审风格列表查询命令对象。
 *
 * @param enabled         可选筛选；非 ADMIN 角色由 ApplicationService 强制覆盖为 true
 * @param currentUserRole 当前用户角色（用于权限决策）
 */
public record ReviewStyleQueryCommand(
    int page,
    int size,
    Boolean enabled,
    String currentUserRole
) {
}
