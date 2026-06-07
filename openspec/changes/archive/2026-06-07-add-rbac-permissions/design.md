# Design: add-rbac-permissions

## 架构决策

### 为何选择 AOP 而非 @PreAuthorize

`@PreAuthorize` 的 SpEL 表达式无法直接引用注解属性，要实现多角色 OR 语义需要写繁琐的 SpEL 字符串（如 `hasAnyRole('ADMIN','TEAM_MEMBER')`），硬编码角色名。

`RoleCheckAspect` 方案：
- `@RequireRole({UserRole.ADMIN, UserRole.TEAM_MEMBER})` — 类型安全，编译期检查
- 方法级覆盖类级，逻辑清晰
- 抛 `BizException(FORBIDDEN)` 走已有 `GlobalExceptionHandler` 通道，响应格式统一

### AccessDeniedHandler 的必要性

虽然 `RoleCheckAspect` 覆盖了业务层权限拦截，但 Spring Security 自身的方法安全（`@PreAuthorize`）触发的 `AccessDeniedException` 默认会走 Spring 的 403 响应，不符合本系统 HTTP 200 + 业务码的约定。`AccessDeniedExceptionHandler` 补齐这一缺口。

## 文件清单

| 文件 | 说明 |
|------|------|
| `api/.../common/security/RequireRole.java` | 角色权限注解（新增） |
| `api/.../common/security/RoleCheckAspect.java` | AOP 拦截器（新增） |
| `api/.../common/security/AccessDeniedExceptionHandler.java` | Spring Security AccessDenied 处理器（新增） |
| `api/pom.xml` | 新增 `spring-boot-starter-aop` 依赖 |
| `bootstrap/.../SecurityConfig.java` | 接入 `accessDeniedHandler`（修改） |
| `api/.../security/RoleCheckAspectTest.java` | 10 个场景单元测试（新增） |
