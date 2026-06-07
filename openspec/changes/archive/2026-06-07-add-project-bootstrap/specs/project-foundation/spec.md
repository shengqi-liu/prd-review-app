## ADDED Requirements

### Requirement: Maven 多模块结构（DDD 分层）
项目 SHALL 采用 Maven 多模块结构组织代码，按 DDD 分层划分为 5 个模块：`api`（接口层：Controller + DTO + 通用响应/错误码/异常）、`application`（应用层：用例编排、事务边界、领域事件订阅）、`domain`（领域层：聚合根、实体、值对象、领域服务、Repository 接口）、`infrastructure`（基础设施层：Repository 实现、外部服务集成、持久化）、`bootstrap`（启动层：`@SpringBootApplication` 与全局配置）。

模块间依赖方向 MUST 严格单向：

```
bootstrap → api → application → domain ← infrastructure
                                  ↑
                               (api 也可依赖 domain 暴露的 DTO 转换契约)
```

- `domain` 模块 MUST NOT 依赖任何其他业务模块
- `infrastructure` 模块 MUST 仅依赖 `domain`（实现其 Repository 接口）
- `application` 模块 MUST NOT 依赖 `infrastructure` 与 `bootstrap`
- `api` 模块 MUST NOT 依赖 `infrastructure` 与 `bootstrap`

每个模块内部 SHALL 通过**包级别 Bounded Context 隔离**，包路径采用 `com.prdreview.<context>.<layer-element>`，其中 `<context>` 为业务上下文（如 `auth`、`prd`、`reviewer`、`reviewstyle`、`knowledgebase`、`review`）。同一上下文的代码在不同模块中保持相同的 `<context>` 包前缀。

#### Scenario: 模块依赖方向合规
- **WHEN** 开发者在 `domain` 模块中尝试引用 `infrastructure` 或 `application` 包下的类
- **THEN** Maven 构建 MUST 失败，Enforcer 插件输出依赖方向违规信息

#### Scenario: 启动模块聚合
- **WHEN** 在项目根目录执行 `mvn -pl bootstrap spring-boot:run`
- **THEN** Spring Boot 应用 MUST 成功启动，监听默认端口 8080

#### Scenario: 包级别上下文一致性
- **WHEN** 任意 context（如 `prd`）的代码同时出现在 `api` / `application` / `domain` / `infrastructure` 四个模块中
- **THEN** 四处包路径 MUST 共享相同前缀 `com.prdreview.prd.*`，不允许其他上下文混入

### Requirement: Java 21 与虚拟线程
项目 SHALL 使用 Java 21 作为编译与运行时版本，并启用 Spring Boot 虚拟线程（`spring.threads.virtual.enabled=true`），所有阻塞型 Web 请求 MUST 通过虚拟线程承载。

#### Scenario: 编译版本检查
- **WHEN** 执行 `mvn -v` 并查看项目 `pom.xml` 配置
- **THEN** `<maven.compiler.source>` 与 `<maven.compiler.target>` MUST 为 21

#### Scenario: 虚拟线程生效
- **WHEN** 应用启动后访问任意端点
- **THEN** 处理请求的线程名 MUST 以 `VirtualThread` 开头或来自虚拟线程执行器

### Requirement: 统一响应包装
所有 REST 接口 SHALL 返回统一的响应结构 `Result<T>`，包含 `code`（错误码，0 表示成功）、`message`（提示信息）、`data`（业务数据）、`traceId`（链路追踪 ID）四个字段。HTTP 状态码 MUST 与业务错误码解耦：业务异常一律返回 HTTP 200，错误信息通过 `code` 字段传达；系统级异常（5xx）保留原始 HTTP 状态码。

#### Scenario: 成功响应
- **WHEN** 调用 `/api/v1/ping` 接口
- **THEN** 响应体 MUST 形如 `{"code":0,"message":"success","data":"pong","traceId":"<uuid>"}`，HTTP 状态码 MUST 为 200

#### Scenario: 业务异常响应
- **WHEN** 业务代码抛出 `BizException` 携带错误码 `10001`
- **THEN** 响应体 MUST 形如 `{"code":10001,"message":"<错误描述>","data":null,"traceId":"<uuid>"}`，HTTP 状态码 MUST 为 200

### Requirement: 错误码体系
项目 SHALL 提供集中式错误码枚举 `ErrorCode`，错误码采用 5 位整数编排：`10000–19999` 通用错误、`20000–29999` 鉴权与权限、`30000–39999` PRD 域、`40000–49999` 评审域、`50000–59999` 知识库域、`60000–69999` Reviewer/Style 域、`90000–99999` 系统级错误。每个错误码 MUST 携带默认提示文案，并支持运行时替换占位符。

#### Scenario: 错误码唯一性
- **WHEN** `ErrorCode` 枚举编译完成
- **THEN** 单元测试 MUST 校验所有错误码数值唯一，无重复

#### Scenario: 业务异常携带错误码
- **WHEN** 业务代码 `throw new BizException(ErrorCode.PRD_NOT_FOUND)`
- **THEN** 全局异常处理器 MUST 捕获并返回 `Result` 中 `code=30001`、`message="PRD 不存在"`

### Requirement: 全局异常处理
项目 SHALL 提供 `GlobalExceptionHandler` 集中处理所有未捕获异常：
- `BizException` 返回业务错误码
- `MethodArgumentNotValidException` / `ConstraintViolationException` 返回参数校验错误（错误码 `10002`）
- `Exception`（兜底）返回系统错误（错误码 `99999`）并记录 ERROR 级日志
所有异常响应 MUST 携带 `traceId` 用于排查。

#### Scenario: 参数校验失败
- **WHEN** 提交不满足 `@Valid` 约束的请求体
- **THEN** 响应 MUST 返回错误码 `10002`，`message` 中包含第一条违规字段说明

#### Scenario: 未捕获异常兜底
- **WHEN** 控制器抛出未在处理器中显式声明的运行时异常
- **THEN** 响应 MUST 返回错误码 `99999`，并在服务端日志输出完整堆栈

### Requirement: 日志规范与 traceId
项目 SHALL 使用 Logback 输出 JSON 结构化日志，并通过 Servlet Filter 在请求入口处生成 `traceId`（UUID 去横线）写入 MDC，响应返回时通过响应头 `X-Trace-Id` 暴露。所有业务日志 MUST 自动携带 `traceId` 字段。

#### Scenario: traceId 自动注入
- **WHEN** 客户端发起任意 HTTP 请求
- **THEN** 该请求处理过程中输出的每一条日志 MUST 包含相同的 `traceId`，且响应头 `X-Trace-Id` 与之一致

#### Scenario: 日志 JSON 格式
- **WHEN** 应用以 `prod` profile 启动并产生日志
- **THEN** 日志输出 MUST 为单行 JSON，至少包含 `timestamp`、`level`、`logger`、`thread`、`traceId`、`message` 字段

### Requirement: 多环境配置
项目 SHALL 提供至少三套 Spring profile：`default`（公共配置）、`dev`（本地开发）、`prod`（生产）。通过 `spring.profiles.active` 切换。敏感配置（API Key、数据库密码）MUST 通过环境变量或外部配置文件注入，禁止硬编码至 yml。

#### Scenario: 默认 profile 启动
- **WHEN** 不指定 profile 启动应用
- **THEN** Spring MUST 自动激活 `dev` profile（开发优先），并加载 `application-dev.yml`

#### Scenario: 生产 profile 启动
- **WHEN** 使用 `-Dspring.profiles.active=prod` 启动应用
- **THEN** 应用 MUST 仅加载 `application.yml` 与 `application-prod.yml`，且日志级别 MUST 为 INFO 或更高

### Requirement: 健康检查端点
项目 SHALL 暴露 Spring Boot Actuator 健康检查端点 `/actuator/health`，无需鉴权可访问，返回 `{"status":"UP"}` 形式的健康状态。

#### Scenario: 健康检查可用
- **WHEN** 应用启动后访问 `GET /actuator/health`
- **THEN** 响应 HTTP 状态码 MUST 为 200，响应体 MUST 包含 `"status":"UP"`

### Requirement: OpenAPI 文档
项目 SHALL 接入 springdoc-openapi，自动扫描所有 `@RestController` 并暴露 OpenAPI 3 文档。访问 `/swagger-ui/index.html` MUST 展示交互式 API 文档，访问 `/v3/api-docs` MUST 返回 OpenAPI JSON 描述文件。

#### Scenario: Swagger UI 可访问
- **WHEN** 应用启动后浏览器访问 `/swagger-ui/index.html`
- **THEN** 页面 MUST 加载成功并列出所有已注册的 REST 接口

#### Scenario: API 文档生成
- **WHEN** 在控制器方法上添加 `@Operation(summary = "测试")` 注解
- **THEN** `/v3/api-docs` 返回的 JSON 中该接口的 `summary` 字段 MUST 为 `"测试"`

### Requirement: 项目路线图文档
项目 SHALL 在 `openspec/roadmap.md` 维护一份顶层路线图，登记全部 24 个 change 的 ID、所属阶段、依赖关系、当前状态（NOT_STARTED / IN_PROGRESS / IN_REVIEW / DONE）。每个 change 完成审计归档后 MUST 同步更新该文档。

#### Scenario: 路线图文件存在
- **WHEN** 检查仓库 `openspec/roadmap.md`
- **THEN** 文件 MUST 存在，且至少包含本次 change `add-project-bootstrap` 的条目

#### Scenario: 状态准确性
- **WHEN** `add-project-bootstrap` 完成审计与归档
- **THEN** `openspec/roadmap.md` 中该 change 的状态 MUST 更新为 `DONE`
