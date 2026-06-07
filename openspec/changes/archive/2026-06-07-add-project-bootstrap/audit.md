# 审计报告 — add-project-bootstrap

审计日期：2026-06-07

## 1. 测试覆盖率

| 模块 | 测试文件 | 用例数 | 结果 |
|------|---------|--------|------|
| api | ErrorCodeTest | 4 | ✅ 全绿 |
| api | ResultTest | 6 | ✅ 全绿 |
| api | LayerArchTest | 3 | ✅ 全绿 |
| api | BoundedContextArchTest | 6 | ✅ 全绿 |
| bootstrap | PrdReviewApplicationTests | 1 | ✅ 全绿 |
| **合计** | | **20** | **✅ 全绿** |

覆盖范围：ErrorCode 唯一性、BizException 构造、Result 包装逻辑、ArchUnit 架构约束（9 条）、Spring 上下文加载。

---

## 2. 代码审计（/code-review medium）

发现 4 个问题，全部已修复：

| # | 文件 | 问题 | 严重度 | 处理 |
|---|------|------|--------|------|
| 1 | `GlobalResponseAdvice.java:63` | String 序列化异常被吞，退化为 `Result.toString()` 写出乱码 | 高 | catch 改为抛 `IllegalStateException` |
| 2 | `TraceIdFilter.java:36` | 客户端可通过 `X-Trace-Id` 头注入换行符，导致 Log Injection | 高 | 加过滤换行符 + 截断 64 字符校验 |
| 3 | `GlobalExceptionHandler.java:75` | `@ResponseStatus` 与 `ResponseBodyAdvice` 同时作用，Content-Type 可能被重置 | 中 | 改为 `HttpServletResponse.setStatus(500)` |
| 4 | `PingController.java:37` | 测试异常端点注释写 dev only 但 `@Profile` 已移除，生产暴露攻击面 | 中 | 拆为独立 `DevPingController`，类级加 `@Profile("dev")` |

---

## 3. 安全审计（/security-review）

| 检查项 | 结论 |
|--------|------|
| 敏感配置（密码/API Key）是否硬编码 | ✅ 通过：`application-dev.yml` 使用 `${MYSQL_ROOT_PASSWORD:xxx}` 环境变量注入，`.env` 加入 `.gitignore` |
| 错误响应是否泄露内部信息 | ✅ 通过：兜底 handler 返回 `SYSTEM_ERROR` 通用消息，不暴露堆栈 |
| 日志是否打印密码/Token | ✅ 通过：日志仅记录 code 和 message，无敏感字段 |
| 用户可控输入写入日志（Log Injection） | ⚠️ 发现 → 已修复（TraceIdFilter 过滤换行符） |
| 测试端点在生产环境暴露 | ⚠️ 发现 → 已修复（DevPingController `@Profile("dev")`） |

---

## 4. Spec 验收（逐条）

对照 `specs/project-foundation/spec.md`：

| Requirement | 验收结论 |
|-------------|---------|
| Maven 多模块结构（5 层 DDD，单向依赖） | ✅ 5 模块落地，Enforcer + ArchUnit 双重守护 |
| Java 21 与虚拟线程 | ✅ 编译目标 21，`spring.threads.virtual.enabled=true` |
| 统一响应包装 `Result<T>` | ✅ 自动包装，跳过非业务路径，String 特殊处理已修复 |
| 错误码体系（5 位分段） | ✅ 24 个错误码，7 段，唯一性单测保障 |
| 全局异常处理 | ✅ BizException / 参数校验 / 兜底 3 类，HTTP 状态码修复 |
| 日志规范与 traceId | ✅ Logback 双 profile，TraceIdFilter，Log Injection 已修复 |
| 多环境配置 | ✅ dev / prod 分离，敏感项环境变量注入 |
| 健康检查端点 | ✅ `/actuator/health` → `{"status":"UP"}` |
| OpenAPI 文档 | ✅ Swagger UI 可达，`/v3/api-docs` HTTP 200 |
| 项目路线图文档 | ✅ `openspec/roadmap.md` 已创建，24 个 change 全部登记 |

**全部 10 条 requirements 验收通过 ✅**

---

## 5. 已知待办（不影响归档）

- task 10.7：虚拟线程并发压测（50 并发），留到 CI 环境执行
- task 10.10：prod profile 启动 JSON 日志格式验证，留到部署阶段
- `DevPingController` 在后续 change 成熟后可整体删除

---

## 结论

**change `add-project-bootstrap` 审计通过，可以归档。**
