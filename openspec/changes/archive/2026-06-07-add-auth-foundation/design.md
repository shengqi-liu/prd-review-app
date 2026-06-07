## Context

项目已有 5 层 DDD 模块骨架（`domain` / `application` / `infrastructure` / `api` / `bootstrap`）、统一响应 `Result<T>`、`ErrorCode`、`BizException`、`TraceIdFilter`、`GlobalExceptionHandler`。本 change 在此基础上叠加认证能力，不破坏已有结构。

数据库已就绪：MySQL 9.7，数据库 `prd_review`，utf8mb4 字符集，连接配置通过 `application-dev.yml` 环境变量注入。

**认证方案选型背景**：系统为内部产品评审工具，用户规模百人级，无需 OAuth2 / SSO，JWT 无状态方案满足需求且实现简单。

## Goals / Non-Goals

**Goals:**
- 用户注册 + 登录 + JWT 令牌全链路
- Spring Security 6 接入，白名单配置
- MyBatis-Plus 持久化用户数据
- `CurrentUser` 工具供后续 change 使用
- 新增 `ErrorCode` 条目（用户域 20010–20013）

**Non-Goals:**
- 角色权限细粒度控制（留给 `add-rbac-permissions`）
- Token 刷新 / 黑名单 / 登出（一期不需要）
- 第三方登录（企业微信 / GitHub）
- 密码重置 / 邮件验证

## Decisions

### 决策 1：JWT 库选型 — JJWT 0.12.x

**选择**：`io.jsonwebtoken:jjwt-api:0.12.6` + `jjwt-impl` + `jjwt-jackson`

**理由**：
- Spring Boot 3 生态主流选择，与 Jakarta EE 9+ 兼容
- API 简洁，Builder 模式友好
- HS256 满足内部系统安全需求，无需 RS256 复杂度

**替代方案**：
- `java-jwt`（Auth0）：API 风格类似，但 JJWT 社区更活跃
- `nimbus-jose-jwt`：适合 OAuth2 / OIDC，对本场景过度

### 决策 2：JWT 放在哪一层

**选择**：`JwtUtil` 放 `api` 模块 `com.prdreview.common.security` 包

**理由**：
- JWT 是 HTTP 层关注点（从请求头解析），不属于领域逻辑
- `application` 层的 `AuthApplicationService` 调用 `JwtUtil` 生成 token，通过 `api` 层依赖路径可达（`application` → `api`）

> 注意：`api` 依赖 `application`（接口层调应用层），`application` 同时依赖 `api` 中的 `JwtUtil` 会造成循环依赖。

**修正**：`JwtUtil` 移到 `domain` 模块 `com.prdreview.common.util` 包（domain 被所有层依赖，无循环风险）。`AuthApplicationService` 从 domain 引用 `JwtUtil`。

### 决策 3：Security 异常响应格式

**选择**：自定义 `AuthenticationEntryPoint` + `AccessDeniedHandler`，统一返回 `Result<Void>` JSON，HTTP 200

**理由**：
- 与 `GlobalExceptionHandler` 规范一致，前端只需判断 `code` 字段
- Spring Security 默认返回 HTML 错误页或 401/403，不符合项目约定

**实现**：在 `SecurityConfig` 中注入自定义 EntryPoint，序列化 `Result.error(ErrorCode.UNAUTHORIZED)` 写入响应。

### 决策 4：MyBatis-Plus 配置策略

**选择**：`MybatisPlusAutoConfiguration`（自动装配）+ `@MapperScan` 指定扫描包

**表结构**：
```sql
CREATE TABLE `user` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `username`   VARCHAR(32)  NOT NULL UNIQUE,
  `email`      VARCHAR(128) NOT NULL UNIQUE,
  `password`   VARCHAR(128) NOT NULL COMMENT 'BCrypt 密文',
  `role`       VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTER',
  `status`     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**字段映射**：使用 MyBatis-Plus `@TableField(fill = FieldFill.INSERT)` 自动填充 `createdAt`。

### 决策 5：User 实体在 DDD 分层中的位置

```
domain/com.prdreview.auth/
├── model/
│   ├── User.java          ← 聚合根（@TableName for MyBatis-Plus）
│   ├── UserRole.java      ← 枚举
│   └── UserStatus.java    ← 枚举
├── repository/
│   └── UserRepository.java ← Repository 接口（domain 定义）
└── service/
    └── (领域服务，本 change 暂无)

infrastructure/com.prdreview.auth/
├── mapper/
│   └── UserMapper.java    ← MyBatis-Plus BaseMapper
└── repository/
    └── UserRepositoryImpl.java ← 实现 domain 的 UserRepository

application/com.prdreview.auth/
└── service/
    └── AuthApplicationService.java ← 注册/登录用例

api/com.prdreview.auth/
└── controller/
    └── AuthController.java

api/com.prdreview.common.security/
├── JwtUtil.java           ← 移至此处（见决策 3 修正：实际在 domain）
├── JwtAuthenticationFilter.java
└── CurrentUser.java

bootstrap/com.prdreview.bootstrap.config/
└── SecurityConfig.java
```

**修正**：`JwtUtil` 位于 `domain/com.prdreview.common.util`，`CurrentUser` 位于 `api/com.prdreview.common.security`。

### 决策 6：新增 ErrorCode 条目

在 `ErrorCode` 枚举 20000 段新增：
```java
USERNAME_EXISTS(20010, "用户名已存在"),
EMAIL_EXISTS(20011, "邮箱已被注册"),
LOGIN_FAILED(20012, "用户名或密码错误"),
ACCOUNT_DISABLED(20013, "账号已禁用"),
```

### 决策 7：JWT Secret Key 管理

- Dev 环境：`application-dev.yml` 中 `jwt.secret: ${JWT_SECRET:dev-secret-key-at-least-32-chars!!}` 提供默认值
- Prod 环境：`application-prod.yml` 中 `jwt.secret: ${JWT_SECRET}` 无默认值，强制从环境变量注入
- `JwtUtil` 通过 `@Value("${jwt.secret}")` 注入，Bean 初始化时校验长度 ≥ 32

## Risks / Trade-offs

- **【风险】JWT 无状态无法主动失效**：用户改密后旧 token 在过期前仍有效 → 缓解：一期接受此限制，后续可引入 token 版本号或 Redis 黑名单
- **【风险】MyBatis-Plus 与 domain 实体注解耦合**（`@TableName`、`@TableField`）→ 缓解：接受务实做法，`@TableName` 是纯元数据注解，不影响领域行为；后续如需解耦可引入 DO（Data Object）层
- **【风险】Spring Security 6 配置变更较大**（废弃了 `WebSecurityConfigurerAdapter`）→ 缓解：使用 Lambda DSL 配置，代码量少，Spring Boot 3.x 文档完善
- **【权衡】自定义 EntryPoint 返回 HTTP 200 而非标准 401**：不符合 REST 规范，但与项目统一响应约定一致，前端处理更简单

## Open Questions

- `add-rbac-permissions` 是否需要在 `SecurityConfig` 中加 `@PreAuthorize` 支持？本 change 先开启 `@EnableMethodSecurity`，后续 change 直接用
- 是否需要在本 change 为管理员预置初始账号（seed data）？倾向不做，留给运维手动创建或后续数据初始化 change
