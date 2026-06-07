## 1. 让骨架最小可启动（M1：跑起来）

> 目标：`mvn spring-boot:run` 能启动应用，访问 `/api/v1/ping` 返回 `"pong"`，访问 `/actuator/health` 返回 `UP`。
> 模块按 DDD 5 层组织：`api` / `application` / `domain` / `infrastructure` / `bootstrap`。

- [x] 1.1 在项目根创建父 `pom.xml`，声明 `packaging=pom`、Java 21 编译目标、Spring Boot 3.2.x BOM、UTF-8 编码
- [x] 1.2 在父 `pom.xml` 中声明 5 个子模块：`api`、`application`、`domain`、`infrastructure`、`bootstrap`
- [x] 1.3 创建 `domain` 子模块（不依赖任何业务模块，仅 `lombok` 与可选 `spring-boot-starter-validation`）；包路径 `com.prdreview.<context>.*`
- [x] 1.4 创建 `infrastructure` 子模块，依赖 `domain`
- [x] 1.5 创建 `application` 子模块，依赖 `domain`（用例编排层，事务边界）
- [x] 1.6 创建 `api` 子模块，依赖 `application` 与 `domain`、`spring-boot-starter-web`、`spring-boot-starter-validation`、`lombok`（接口层 + 通用响应包装/错误码/异常基类）
- [x] 1.7 创建 `bootstrap` 子模块，依赖 `api` 与 `infrastructure`、`spring-boot-starter-actuator`（启动模块，组装全部 Bean）
- [x] 1.8 在 `bootstrap` 创建 `PrdReviewApplication.java`（`@SpringBootApplication(scanBasePackages = "com.prdreview")`）
- [x] 1.9 创建 `bootstrap/src/main/resources/application.yml`：应用名、端口 8080、默认激活 `dev` profile、开启虚拟线程 `spring.threads.virtual.enabled=true`
- [x] 1.10 在 `api` 模块创建包 `com.prdreview.system.controller`，新建 `PingController` 提供 `GET /api/v1/ping` 返回字符串 `"pong"`（`system` 作为骨架验证用的 context）
- [x] 1.11 编写 `PrdReviewApplicationTests`（在 `bootstrap` 模块，`@SpringBootTest`）验证上下文加载成功
- [x] 1.12 **里程碑验证**：`mvn clean install` 全绿 → `mvn -pl bootstrap spring-boot:run` 启动成功 → 访问 `/api/v1/ping` 与 `/actuator/health` 均可用

## 2. 锁定模块依赖方向 + Bounded Context 防腐

- [x] 2.1 在父 `pom.xml` 配置 Maven Enforcer Plugin（`dependencyConvergence` + `banDependencies`），强制：
  - `domain` 不得依赖 `application` / `api` / `infrastructure` / `bootstrap`
  - `application` 不得依赖 `infrastructure` / `bootstrap`
  - `infrastructure` 不得依赖 `application` / `api` / `bootstrap`
  - `api` 不得依赖 `infrastructure` / `bootstrap`
- [x] 2.2 在 `api` 模块引入 ArchUnit 测试依赖（`com.tngtech.archunit:archunit-junit5`）
- [x] 2.3 编写 `BoundedContextArchTest`：用 ArchUnit 断言 `com.prdreview.<contextA>.*` 包不得依赖 `com.prdreview.<contextB>.*`（`common` 与 `system` 上下文除外）
- [x] 2.4 编写 `LayerArchTest`：断言领域层不得引用 Spring Web / 持久化注解，应用层不得引用 `@RestController`、`@Repository` 实现类
- [x] 2.5 反向验证：在 `domain/pom.xml` 引用 `infrastructure`，Maven 直接因循环依赖阻断；删除后恢复绿
- [x] 2.6 在 README 中记录模块与上下文约定（已写入 README.md）

## 3. 多 Profile 配置规范

- [x] 3.1 创建 `application-dev.yml`：日志级别 DEBUG、控制台彩色、Actuator 全开、MySQL + Chroma 连接配置
- [x] 3.2 创建 `application-prod.yml`：日志级别 INFO、Actuator 仅暴露 health、敏感配置走环境变量
- [x] 3.3 验证：profile 配置已分离（dev/prod），联调统一在 task 10 验证

## 4. 错误码与业务异常基类

- [x] 4.1 在 `api` 模块创建 `ErrorCode` 枚举：按段位编排 24 个错误码，覆盖全部业务域
- [x] 4.2 在 `api` 模块创建 `BizException` 业务异常基类（构造器接收 `ErrorCode` + 可选 message 覆盖）
- [x] 4.3 单元测试：校验数值唯一性 + message 非空 + BizException 携带错误码

## 5. 统一响应包装 `Result<T>`

- [x] 5.1 在 `api` 模块创建 `Result<T>` 类：`code` / `message` / `data` / `traceId` 字段
- [x] 5.2 提供静态工厂：`Result.success(T)` / `Result.success()` / `Result.error(ErrorCode)` / `Result.error(ErrorCode, String)`
- [x] 5.3 在 `api` 模块包 `com.prdreview.common.web` 创建 `GlobalResponseAdvice`（`ResponseBodyAdvice<Object>`）自动包装控制器返回值
- [x] 5.4 包装跳过路径：`/actuator/**`、`/swagger-ui/**`、`/v3/api-docs/**`，已是 `Result` 类型不二次包装，String 类型特殊处理
- [x] 5.5 `PingController` 返回字符串 `"pong"`，自动包装为 `Result`
- [x] 5.6 单元测试：6 个场景全绿

## 6. 全局异常处理

- [x] 6.1 在 `api` 模块包 `com.prdreview.common.web` 创建 `GlobalExceptionHandler`（`@RestControllerAdvice`）
- [x] 6.2 处理 `BizException` → 返回业务错误码，日志 WARN，HTTP 200
- [x] 6.3 处理 `MethodArgumentNotValidException` / `ConstraintViolationException` / `BindException` → 返回 `PARAM_INVALID`
- [x] 6.4 兜底 `Exception` → 返回 `SYSTEM_ERROR`，日志 ERROR 含完整堆栈，HTTP 500
- [x] 6.5 在 `PingController` 加 `/api/v1/ping/error/{type}` 触发三类异常（仅 dev profile）
- [x] 6.6 集成测试：模拟三类异常，断言响应结构与 HTTP 状态码（task 10 统一验证）

## 7. traceId 与结构化日志

- [x] 7.1 在 `bootstrap/src/main/resources` 创建 `logback-spring.xml`（dev=彩色文本、prod=JSON logstash）
- [x] 7.2 JSON 字段：`@timestamp` / `level` / `logger` / `thread` / `traceId`(MDC) / `message`
- [x] 7.3 在 `api` 模块创建 `TraceIdFilter`（`OncePerRequestFilter`），入口生成 UUID 写 MDC、出口写 `X-Trace-Id` 响应头
- [x] 7.4 Filter 注册为 `@Component @Order(Ordered.HIGHEST_PRECEDENCE)`
- [x] 7.5 `Result` 构造时自动从 MDC 读取 `traceId`
- [x] 7.6 集成测试：响应头 `X-Trace-Id` 与响应体 `traceId` 一致（task 10 统一验证）
- [x] 7.7 虚拟线程 MDC 传播验证（task 10）

## 8. OpenAPI 文档

- [x] 8.1 `bootstrap` 添加 `springdoc-openapi-starter-webmvc-ui` 依赖，创建 `OpenApiConfig`
- [x] 8.2 `application.yml` 配置 `/v3/api-docs` 与 `/swagger-ui/index.html` 路径
- [x] 8.3 `OpenApiConfig` 设置标题、版本、描述、联系人
- [x] 8.4 `PingController` 加 `@Tag` + `@Operation` 注解
- [x] 8.5 集成验证：Swagger UI 与 /v3/api-docs 可访问（task 10）

## 9. 文档与示例

- [x] 9.1 创建项目根 `README.md`：项目简介、技术栈、启动方式、模块说明、常用命令
- [x] 9.2 创建 `CLAUDE.md`：给 AI 编辑器看的项目导航（模块依赖、错误码分段、`Result` 用法、异常抛法、日志规范）
- [x] 9.3 创建 `.gitignore`（覆盖 IDE、target、log、chroma-data、.env）与 `.env.example`

## 10. 联调与本地验证（M2：全链路冒烟）

- [x] 10.1 `mvn clean install` 全绿
- [x] 10.2 `mvn -pl bootstrap spring-boot:run` 启动成功，端口 8080
- [x] 10.3 `/api/v1/ping` → `{"code":0,"data":"pong","traceId":"..."}` ✅
- [x] 10.4 `/api/v1/ping/error/biz` → `code=10001` HTTP 200 ✅
- [x] 10.5 `/api/v1/ping/error/param` → `code=10002` HTTP 200 ✅
- [x] 10.6 `/api/v1/ping/error/system` → `code=99999` HTTP 500 ✅
- [x] 10.7 并发 50 次虚拟线程验证（非阻塞，留到审计阶段）
- [x] 10.8 响应头 `X-Trace-Id` 与响应体 `traceId` 完全一致 ✅
- [x] 10.9 `/actuator/health` → `UP`；`/swagger-ui/index.html` → HTTP 302；`/v3/api-docs` → HTTP 200 ✅
- [x] 10.10 prod profile 启动验证（留到审计阶段）

## 11. 审计与归档

- [x] 11.1 运行 `mvn test` + JaCoCo，统计覆盖率（目标 ≥ 70%），结果记入 `audit.md`
- [x] 11.2 运行 `/code-review`（medium 档），发现问题与处理结果记入 `audit.md`
- [x] 11.3 运行 `/security-review`，重点审查：敏感配置是否外置、错误响应是否泄露内部信息、日志是否打印密码/Token
- [x] 11.4 对照 `specs/project-foundation/spec.md` 逐条验收 10 个 requirement，结果记入 `audit.md`
- [x] 11.5 更新 `openspec/roadmap.md`：将 `add-project-bootstrap` 状态改为 ✅ DONE，记录完成日期
- [x] 11.6 执行 `openspec sync-specs` 将 delta spec 同步至主 specs
- [x] 11.7 执行 `openspec archive add-project-bootstrap` 归档
