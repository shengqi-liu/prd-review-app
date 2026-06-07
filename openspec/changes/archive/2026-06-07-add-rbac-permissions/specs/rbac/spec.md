## ADDED Requirements

### Requirement: 角色权限注解
系统 SHALL 提供 `@RequireRole(UserRole... value)` 注解，可标注在 Controller 方法或类上，声明允许访问的角色列表（OR 关系）。

#### Scenario: 单角色限制 — 匹配
- **WHEN** 当前用户角色在 `@RequireRole` 声明的列表内
- **THEN** 方法正常执行，不抛出任何异常

#### Scenario: 单角色限制 — 不匹配
- **WHEN** 当前用户角色不在 `@RequireRole` 声明的列表内
- **THEN** 系统 MUST 抛出 `BizException(ErrorCode.FORBIDDEN)`，最终由 GlobalExceptionHandler 返回 `{"code":20002,"message":"无权限执行此操作"}`

#### Scenario: 多角色（OR 关系）
- **WHEN** `@RequireRole({ADMIN, TEAM_MEMBER})`，当前用户为 TEAM_MEMBER
- **THEN** 方法正常执行

#### Scenario: 未登录访问受注解保护的方法
- **WHEN** SecurityContextHolder 中无有效 Authentication
- **THEN** 系统 MUST 抛出 `BizException(ErrorCode.UNAUTHORIZED)`（由 CurrentUser 工具类抛出）

#### Scenario: 方法级注解覆盖类级注解
- **WHEN** 类上标注 `@RequireRole(ADMIN)`，方法上标注 `@RequireRole(SUBMITTER)`，当前用户为 SUBMITTER
- **THEN** 方法级注解生效，SUBMITTER 可访问

---

### Requirement: 角色权限 AOP 拦截器
系统 SHALL 实现 `RoleCheckAspect`，拦截所有标注了 `@RequireRole` 的方法（方法级和类级），从 `CurrentUser` 获取当前角色，校验权限。

#### Scenario: 方法级注解优先
- **WHEN** 方法和类均有 `@RequireRole`
- **THEN** 方法级注解 MUST 优先于类级注解

---

### Requirement: 权限拒绝处理器
系统 SHALL 实现 `AccessDeniedExceptionHandler`，处理 Spring Security 层面的 `AccessDeniedException`（如 `@PreAuthorize` 不通过），返回 `{"code":20002,"message":"无权限执行此操作"}`，HTTP 200。

#### Scenario: 权限不足响应格式
- **WHEN** 触发 AccessDeniedException
- **THEN** 响应 MUST 为 `{"code":20002,"message":"无权限执行此操作"}`，HTTP status MUST 为 200
