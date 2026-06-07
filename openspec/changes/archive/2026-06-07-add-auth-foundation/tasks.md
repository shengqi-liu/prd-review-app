## 1. 添加依赖

- [x] 1.1 在 `bootstrap/pom.xml` 添加 `spring-boot-starter-security`
- [x] 1.2 在父 `pom.xml` dependencyManagement 添加 `jjwt-api:0.12.6`、`jjwt-impl`、`jjwt-jackson`；在 `domain/pom.xml` 引入 `jjwt-api`，在 `bootstrap/pom.xml` 引入 `jjwt-impl`、`jjwt-jackson`
- [x] 1.3 在父 `pom.xml` dependencyManagement 添加 `mybatis-plus-boot-starter:3.5.7`、`mysql-connector-j`；在 `infrastructure/pom.xml` 引入 `mybatis-plus-boot-starter`，在 `bootstrap/pom.xml` 引入 `mysql-connector-j`
- [x] 1.4 在 `application-dev.yml` 补充 `spring.datasource.*` 配置（已有占位）和 `jwt.secret`、`jwt.expiration` 配置项
- [x] 1.5 `mvn clean install -DskipTests` 验证依赖解析全部成功

## 2. 数据库建表

- [x] 2.1 在项目根创建 `db/migration/V1__create_user_table.sql`，包含 `user` 表 DDL（utf8mb4、字段注释、唯一索引）
- [x] 2.2 执行 SQL 脚本在本地 MySQL 创建 `user` 表，验证表结构

## 3. 领域层 — User 聚合根

- [x] 3.1 创建 `domain/com.prdreview.auth.model.UserRole` 枚举：`SUBMITTER`、`TEAM_MEMBER`、`ADMIN`
- [x] 3.2 创建 `domain/com.prdreview.auth.model.UserStatus` 枚举：`ACTIVE`、`DISABLED`
- [x] 3.3 创建 `domain/com.prdreview.auth.model.User` 实体类（`@TableName("user")`、`@TableId`、`@TableField`），包含全部字段与 `createdAt` 自动填充
- [x] 3.4 创建 `domain/com.prdreview.auth.repository.UserRepository` 接口：`findByUsername`、`findByEmail`、`findById`、`save` 四个方法
- [x] 3.5 在 `domain/com.prdreview.common.util` 创建 `JwtUtil`：注入 `jwt.secret` / `jwt.expiration`，提供 `generateToken(User)`、`parseToken(String)`、`isTokenExpired(String)` 三个方法；Bean 初始化时校验 secret 长度 ≥ 32
- [x] 3.6 在 `ErrorCode` 枚举 20000 段补充：`USERNAME_EXISTS(20010)`、`EMAIL_EXISTS(20011)`、`LOGIN_FAILED(20012)`、`ACCOUNT_DISABLED(20013)`

## 4. 基础设施层 — MyBatis-Plus 实现

- [x] 4.1 创建 `infrastructure/com.prdreview.auth.mapper.UserMapper` 接口（继承 `BaseMapper<User>`）
- [x] 4.2 创建 `infrastructure/com.prdreview.auth.repository.UserRepositoryImpl` 实现 `UserRepository`，委托 `UserMapper` 完成查询
- [x] 4.3 创建 `infrastructure/com.prdreview.common.handler.MetaObjectHandlerConfig`（实现 `MetaObjectHandler`），自动填充 `createdAt`
- [x] 4.4 在 `bootstrap` 的 `PrdReviewApplication` 或独立 Config 上添加 `@MapperScan("com.prdreview.**.mapper")`
- [x] 4.5 启动应用，验证 MyBatis-Plus 正常初始化、`user` 表可访问

## 5. 应用层 — 认证用例

- [x] 5.1 创建 `application/com.prdreview.auth.dto.RegisterCommand`（username、email、password）和 `LoginCommand`（username、password）
- [x] 5.2 创建 `application/com.prdreview.auth.dto.LoginResult`（accessToken、tokenType、expiresIn）和 `UserDTO`（id、username、email、role，不含 password）
- [x] 5.3 创建 `application/com.prdreview.auth.service.AuthApplicationService`：
  - `register(RegisterCommand)` → 校验唯一性 → BCrypt 加密 → 保存 → 返回 `UserDTO`
  - `login(LoginCommand)` → 查用户 → 验密 → 校验 status → 生成 JWT → 返回 `LoginResult`
- [x] 5.4 单元测试 `AuthApplicationServiceTest`：注册重复用户名/邮件抛异常、登录密码错误抛异常、账号禁用抛异常

## 6. 接口层 — Controller + 安全组件

- [x] 6.1 创建 `api/com.prdreview.auth.controller.AuthController`：
  - `POST /api/v1/auth/register`（`@Valid RegisterRequest`）
  - `POST /api/v1/auth/login`（`@Valid LoginRequest`）
  - 两个接口均标注 `@Operation` OpenAPI 注解
- [x] 6.2 创建 `api/com.prdreview.auth.dto.RegisterRequest`（username/email/password 含 `@Valid` 约束）和 `LoginRequest`
- [x] 6.3 创建 `api/com.prdreview.common.security.JwtAuthenticationFilter`（`OncePerRequestFilter`）：
  - 从 `Authorization: Bearer <token>` 提取并验证 JWT
  - 验证通过 → 构造 `UsernamePasswordAuthenticationToken` 写入 `SecurityContextHolder`
  - token 无效/过期 → 放行请求（由 EntryPoint 处理 401）
- [x] 6.4 创建 `api/com.prdreview.common.security.CurrentUser` 工具类：`getCurrentUserId()`、`getCurrentUsername()`、`getCurrentUserRole()`，未登录时抛 `BizException(UNAUTHORIZED)`
- [x] 6.5 创建 `api/com.prdreview.common.security.SecurityAuthenticationEntryPoint`（实现 `AuthenticationEntryPoint`）：返回 `Result.error(ErrorCode.UNAUTHORIZED)`，HTTP 200

## 7. 启动层 — SecurityConfig

- [x] 7.1 在 `bootstrap/com.prdreview.bootstrap.config` 创建 `SecurityConfig`（`@Configuration @EnableWebSecurity @EnableMethodSecurity`）
- [x] 7.2 配置 `SecurityFilterChain`：
  - 禁用 CSRF（无状态）、禁用 Session（`STATELESS`）
  - 白名单放行：`/api/v1/auth/**`、`/actuator/health`、`/swagger-ui/**`、`/v3/api-docs/**`、`/webjars/**`
  - 其余 `/api/**` 需认证
  - 注册 `JwtAuthenticationFilter`（在 `UsernamePasswordAuthenticationFilter` 之前）
  - 注册自定义 `SecurityAuthenticationEntryPoint`
- [x] 7.3 配置 `PasswordEncoder` Bean（BCryptPasswordEncoder）
- [x] 7.4 验证：未携带 token 访问 `/api/v1/ping` → 返回 `code=20001`；携带有效 token → 正常返回

## 8. 联调验证

- [x] 8.1 注册新用户：`POST /api/v1/auth/register`，验证返回 UserDTO、数据库有记录、密码为 BCrypt 格式
- [x] 8.2 重复注册：验证返回 `code=20010` 或 `code=20011`
- [x] 8.3 登录成功：`POST /api/v1/auth/login`，验证返回包含 JWT token
- [x] 8.4 密码错误：验证返回 `code=20012`
- [x] 8.5 携带有效 JWT 访问 `/api/v1/ping`：验证返回 `code=0`
- [x] 8.6 无 JWT 访问 `/api/v1/ping`：验证返回 `code=20001`
- [x] 8.7 JWT 过期验证（临时将过期时间改为 1s，等待过期后访问）：验证返回 `code=20004`
- [x] 8.8 Swagger UI 验证：在 `/swagger-ui/index.html` 中添加 Bearer token 后调用受保护接口成功

## 9. 审计与归档

- [x] 9.1 运行 `mvn test`，全部用例通过，结果记入 `audit.md`
- [x] 9.2 运行 `/code-review`（medium 档），发现问题与处理结果记入 `audit.md`
- [x] 9.3 运行 `/security-review`（重点：密码存储、JWT secret 注入、Log Injection、用户名枚举防护）
- [x] 9.4 对照 `specs/auth/spec.md` 逐条验收，结果记入 `audit.md`
- [x] 9.5 更新 `openspec/roadmap.md`：`add-auth-foundation` 状态改为 ✅ DONE
- [x] 9.6 执行 `openspec-sync-specs` 同步主 specs
- [x] 9.7 执行 `openspec-archive-change` 归档
