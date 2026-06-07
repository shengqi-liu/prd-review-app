package com.prdreview.common.security;

import com.prdreview.auth.model.UserRole;

import java.lang.annotation.*;

/**
 * 角色权限注解，用于 Controller 方法或类级别。
 *
 * <p>底层由 {@link RoleCheckAspect} 拦截，权限不足时抛出
 * {@code BizException(FORBIDDEN)}，由 GlobalExceptionHandler 统一返回
 * {@code {"code":20002,"message":"无权限执行此操作"}}。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 仅管理员可访问
 * @RequireRole(UserRole.ADMIN)
 * @GetMapping("/users")
 * public List<UserDTO> listUsers() { ... }
 *
 * // 管理员或团队成员均可访问（OR 关系）
 * @RequireRole({UserRole.ADMIN, UserRole.TEAM_MEMBER})
 * @GetMapping("/reports")
 * public List<ReportDTO> listReports() { ... }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /**
     * 允许访问的角色列表（OR 关系，至少一个匹配即通过）。
     */
    UserRole[] value();
}
