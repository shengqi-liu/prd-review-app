## ADDED Requirements

### Requirement: 用户实体
系统 SHALL 维护 `User` 聚合根，包含以下字段：`id`（Long，自增主键）、`username`（唯一，2–32字符）、`email`（唯一，合法邮箱格式）、`password`（BCrypt 加密存储，明文禁止落库）、`role`（枚举：`SUBMITTER` / `TEAM_MEMBER` / `ADMIN`，默认 `SUBMITTER`）、`status`（枚举：`ACTIVE` / `DISABLED`，默认 `ACTIVE`）、`createdAt`（创建时间，自动填充）。

#### Scenario: 密码不可逆加密
- **WHEN** 用户注册时提交明文密码
- **THEN** 系统 MUST 使用 BCrypt 加密后存储，数据库中 MUST NOT 出现明文密码

#### Scenario: 用户名唯一约束
- **WHEN** 注册时使用已存在的 username
- **THEN** 系统 MUST 返回错误码 `20010`（用户名已存在），HTTP 200

#### Scenario: 邮箱唯一约束
- **WHEN** 注册时使用已存在的 email
- **THEN** 系统 MUST 返回错误码 `20011`（邮箱已被注册），HTTP 200

---

### Requirement: 用户注册
系统 SHALL 提供 `POST /api/v1/auth/register` 接口，接受 `username`、`email`、`password` 三个字段，注册成功后返回用户基本信息（不含密码）。

#### Scenario: 注册成功
- **WHEN** 提交合法的 username / email / password
- **THEN** 系统 MUST 创建用户，返回 `{"code":0,"data":{"id":1,"username":"xxx","email":"xxx","role":"SUBMITTER"}}`

#### Scenario: 参数校验
- **WHEN** username 长度不在 2–32 字符范围内，或 email 格式非法，或 password 少于 8 位
- **THEN** 系统 MUST 返回错误码 `10002`（参数不合法）

---

### Requirement: 用户登录
系统 SHALL 提供 `POST /api/v1/auth/login` 接口，接受 `username`（或 email）与 `password`，验证通过后返回 JWT access token。

#### Scenario: 登录成功
- **WHEN** 提交正确的 username 和 password
- **THEN** 系统 MUST 返回 `{"code":0,"data":{"accessToken":"<jwt>","tokenType":"Bearer","expiresIn":86400}}`

#### Scenario: 密码错误
- **WHEN** username 存在但 password 不匹配
- **THEN** 系统 MUST 返回错误码 `20012`（用户名或密码错误），HTTP 200，不得区分"用户不存在"与"密码错误"（防止用户名枚举攻击）

#### Scenario: 账号已禁用
- **WHEN** 用户 status 为 DISABLED 时尝试登录
- **THEN** 系统 MUST 返回错误码 `20013`（账号已禁用），HTTP 200

---

### Requirement: JWT 令牌
系统 SHALL 使用 JJWT 库（HS256 算法）生成和验证 JWT。令牌有效期 24 小时，携带 `userId`、`username`、`role` 三个 claim。JWT secret key MUST 通过环境变量 `JWT_SECRET` 注入，长度 MUST ≥ 32 字符，禁止硬编码。

#### Scenario: 令牌生成
- **WHEN** 用户登录成功
- **THEN** 系统 MUST 生成包含 `userId`、`username`、`role`、`iat`、`exp` 的 JWT

#### Scenario: 令牌过期
- **WHEN** 使用已过期的 JWT 访问受保护接口
- **THEN** 系统 MUST 返回错误码 `20004`（Token 已过期），HTTP 200

#### Scenario: 令牌非法
- **WHEN** 使用伪造或被篡改的 JWT 访问受保护接口
- **THEN** 系统 MUST 返回错误码 `20003`（Token 无效），HTTP 200

---

### Requirement: JWT 认证过滤器
系统 SHALL 在每次 HTTP 请求时，通过 `JwtAuthenticationFilter` 解析请求头 `Authorization: Bearer <token>`，验证通过后将用户信息写入 `SecurityContextHolder`，供后续业务代码获取。过滤器 MUST NOT 阻断白名单路径的请求。

#### Scenario: 有效 token 写入安全上下文
- **WHEN** 请求携带有效 JWT
- **THEN** `SecurityContextHolder` MUST 包含对应用户的 `Authentication` 对象，`getPrincipal()` 返回用户信息

#### Scenario: 无 token 访问白名单
- **WHEN** 无 Authorization 头访问 `/api/v1/auth/login`
- **THEN** 请求 MUST 正常通过，不返回 401

---

### Requirement: 接口访问控制
系统 SHALL 通过 Spring Security 6 无状态（Stateless）配置控制接口访问。白名单（无需认证）：`/api/v1/auth/**`、`/actuator/health`、`/swagger-ui/**`、`/v3/api-docs/**`、`/webjars/**`。其余所有 `/api/**` 接口 MUST 需要有效 JWT 方可访问。

#### Scenario: 未认证访问受保护接口
- **WHEN** 不携带 JWT 访问 `/api/v1/ping`
- **THEN** 系统 MUST 返回错误码 `20001`（未登录），HTTP 200（通过自定义 AuthenticationEntryPoint）

#### Scenario: 白名单接口无需认证
- **WHEN** 不携带 JWT 访问 `/api/v1/auth/login`
- **THEN** 请求 MUST 正常处理，不拦截

---

### Requirement: 当前用户上下文
系统 SHALL 提供 `CurrentUser` 工具类，从 `SecurityContextHolder` 获取当前登录用户信息。提供 `getCurrentUserId()`、`getCurrentUsername()`、`getCurrentUserRole()` 三个静态方法。未登录时调用 MUST 抛出 `BizException(UNAUTHORIZED)`。

#### Scenario: 已登录时获取用户信息
- **WHEN** 在已认证的请求上下文中调用 `CurrentUser.getCurrentUserId()`
- **THEN** 返回值 MUST 与 JWT 中的 `userId` 一致

#### Scenario: 未登录时调用抛异常
- **WHEN** 在未认证上下文中调用 `CurrentUser.getCurrentUserId()`
- **THEN** MUST 抛出 `BizException(ErrorCode.UNAUTHORIZED)`
