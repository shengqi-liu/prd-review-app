## Why

系统需要识别"谁在操作"才能保障数据隔离与权限控制。后续所有业务能力（PRD 提交、评审发起、报告查看）都依赖当前用户身份。本 change 建立最小可用的认证基础：注册 + 登录 + JWT 令牌验证，为 change #4 `add-rbac-permissions` 的角色权限体系打下地基。

## What Changes

- 新建 `User` 聚合根（`domain` 层）：包含 id、username、email、password（BCrypt）、role、status、createdAt 字段
- 新建 `user` 表 DDL 迁移脚本（MySQL，utf8mb4）
- 新建 MyBatis-Plus `UserMapper` 与 `UserRepository` 接口实现（`infrastructure` 层）
- 新建 `AuthApplicationService`（`application` 层）：注册、登录用例编排
- 新建 `JwtUtil`（`api` 层 common）：HS256 生成/解析/验证，过期时间 24h
- 新建 `JwtAuthenticationFilter`（`api` 层 common）：解析 `Authorization: Bearer <token>`，写入 `SecurityContextHolder`
- 新建 `SecurityConfig`（`bootstrap` 层）：Spring Security 6 无状态配置，白名单放行
- 新建 `AuthController`（`api` 层）：`POST /api/v1/auth/register` / `POST /api/v1/auth/login`
- 新建 `CurrentUser` 工具类（`api` 层 common）：从 `SecurityContextHolder` 获取当前登录用户
- `bootstrap/pom.xml` 新增 `spring-boot-starter-security`、`jjwt-api`、`mybatis-plus-boot-starter`、`mysql-connector-j` 依赖

## Capabilities

### New Capabilities
- `auth`：用户认证能力，涵盖用户实体、注册/登录接口、JWT 令牌生命周期、安全上下文过滤器、接口访问控制白名单、当前用户上下文工具。

### Modified Capabilities
- `project-foundation`：`SecurityConfig` 扩展了项目基础设施层，Spring Security 影响所有接口的访问策略（白名单放行已有的 actuator / swagger / v3/api-docs）。

## Impact

- **新增依赖**：`spring-boot-starter-security`、`jjwt-api 0.12.x`、`jjwt-impl`、`jjwt-jackson`、`mybatis-plus-boot-starter 3.5.x`、`mysql-connector-j`
- **新增数据库表**：`user`（MySQL）
- **新增接口**：`POST /api/v1/auth/register`、`POST /api/v1/auth/login`
- **影响已有接口**：Spring Security 接入后，`/api/v1/ping` 在 prod 环境将需要认证（dev 可配置放行）；`/api/v1/ping/error/**` 保持 dev-only
- **后续 change 依赖**：`add-rbac-permissions`（#4）依赖本 change 的 `User` 实体与 `SecurityContext`
- **配置新增**：JWT secret key、token 过期时间通过环境变量注入，禁止硬编码
