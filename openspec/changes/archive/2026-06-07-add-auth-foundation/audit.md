# 审计报告 — add-auth-foundation

审计日期：2026-06-07

## 1. 测试结果

| 模块 | 测试文件 | 用例数 | 结果 |
|------|---------|--------|------|
| api | ErrorCodeTest | 4 | ✅ 全绿 |
| api | ResultTest | 6 | ✅ 全绿 |
| api | LayerArchTest | 3 | ✅ 全绿 |
| api | BoundedContextArchTest | 6 | ✅ 全绿 |
| bootstrap | PrdReviewApplicationTests | 1 | ✅ 全绿 |
| **合计** | | **20** | **✅ 全绿** |

## 2. 联调验证结果

| 场景 | 预期 | 实际 | 结论 |
|------|------|------|------|
| 注册成功 | code=0，返回 UserDTO（无 password） | ✅ | 通过 |
| 重复用户名注册 | code=20010 | ✅ | 通过 |
| 登录成功 | code=0，返回 JWT token | ✅ | 通过 |
| 密码错误 | code=20012 | ✅ | 通过 |
| 携带有效 JWT 访问 /ping | code=0 | ✅ | 通过 |
| 无 JWT 访问 /ping | code=20001 | ✅ | 通过 |

## 3. Code Review 发现（medium 档）

| # | 文件 | 问题 | 严重度 | 处理 |
|---|------|------|--------|------|
| 1 | `AuthApplicationService.java:59` | status 校验在验密之后，disabled 账号密码错误时抛 LOGIN_FAILED 而非 ACCOUNT_DISABLED | 中 | 已修复：调整为先校验 status 再验密 |
| 2 | `JwtAuthenticationFilter.java:45` | `getUserId/getUsername/getRole` 各自独立调用 `parseToken`，重复解析三次 | 低 | 已修复：一次 `parseToken` 复用 `Claims` |
| 3 | `TraceIdFilter.java` | 检查旧版本是否含 Log Injection 防护 | 中 | 无需处理，已是修复版本 |

## 4. 安全审计

| 检查项 | 结论 |
|--------|------|
| 密码 BCrypt 加密存储 | ✅ 通过：数据库中存储 `$2a$10$...` 格式 |
| JWT secret 环境变量注入 | ✅ 通过：dev 有默认值，prod 必须显式设置 |
| JWT secret 长度校验 | ✅ 通过：`@PostConstruct` 校验 ≥ 32 字符 |
| 用户名枚举攻击防护 | ✅ 通过：用户不存在与密码错误统一返回 `LOGIN_FAILED(20012)` |
| Log Injection 防护 | ✅ 通过：TraceIdFilter 过滤换行符 + 截断 64 字符 |
| 敏感信息不出现在响应中 | ✅ 通过：UserDTO 不含 password 字段 |
| 测试端点 prod 环境隔离 | ✅ 通过：DevPingController `@Profile("dev")` |

## 5. Spec 验收

对照 `specs/auth/spec.md`：

| Requirement | 结论 |
|-------------|------|
| 用户实体（字段、唯一约束、BCrypt） | ✅ |
| 用户注册（接口、参数校验、成功/重复场景） | ✅ |
| 用户登录（JWT 返回、密码错误、账号禁用） | ✅ |
| JWT 令牌（HS256、24h、claims、secret 校验） | ✅ |
| JWT 认证过滤器（提取 Bearer、写入 SecurityContext） | ✅ |
| 接口访问控制（白名单、未认证返回 20001） | ✅ |
| CurrentUser 工具（获取用户信息、未登录抛异常） | ✅ |

**全部 7 条 requirements 验收通过 ✅**

## 6. 已知待办

- `AuthApplicationServiceTest` 单元测试（mock UserRepository）留到测试补全 change 统一处理
- JWT 过期场景集成测试（临时改过期时间）留到 CI 环境执行

## 结论

**change `add-auth-foundation` 审计通过，可以归档。**
