## Why

AI 产品方案评审系统需要一个稳固、可扩展的工程地基，为后续 23 个 change（用户体系、PRD 管理、AI 评审员、知识库、评审编排等）提供统一的项目结构、运行时配置、日志、异常处理与 API 规范。在第一个 change 把骨架定型，可以避免后续 change 各自重复造轮子或风格不统一。

## What Changes

- 新建 Maven 多模块项目结构（`api` / `domain` / `infrastructure` / `app`），约束依赖方向，落实分层架构
- 引入 Spring Boot 3.2+ 基础框架，开启 Java 21 虚拟线程支持
- 落地统一 HTTP 响应包装 `Result<T>` 与全局异常处理器 `GlobalExceptionHandler`
- 落地错误码体系（`ErrorCode` 枚举 + 业务异常基类 `BizException`）
- 落地日志规范：Logback 配置 + traceId（MDC + Filter 自动注入），所有日志输出 JSON 格式
- 落地多环境配置：`application.yml`（默认）/ `application-dev.yml` / `application-prod.yml`
- 接入 OpenAPI 3（springdoc-openapi），自动暴露 `/swagger-ui` 与 `/v3/api-docs`
- 暴露 Spring Boot Actuator 健康检查端点 `/actuator/health`
- 新增项目顶层路线图 `openspec/roadmap.md`，登记全部 24 个 change 的 ID、依赖关系、当前状态

## Capabilities

### New Capabilities
- `project-foundation`: 项目骨架能力，约定 Maven 模块结构、Spring Boot 运行时、配置约定、统一异常与响应、日志与 traceId、OpenAPI 文档与健康检查等基础设施级要求。

### Modified Capabilities
（无）

## Impact

- **新增依赖**：`spring-boot-starter-web`、`spring-boot-starter-actuator`、`spring-boot-starter-validation`、`springdoc-openapi-starter-webmvc-ui`、`logstash-logback-encoder`、`lombok`
- **新增目录**：
  - 项目根：`pom.xml`、`api/`、`domain/`、`infrastructure/`、`app/`
  - 配置：`app/src/main/resources/application{,-dev,-prod}.yml`、`logback-spring.xml`
  - 文档：`openspec/roadmap.md`
- **新增端点**：`/actuator/health`、`/swagger-ui/index.html`、`/v3/api-docs`、`/api/v1/ping`（用于验证骨架可运行）
- **后续 change 依赖**：所有阶段二及之后的 change 都依赖该骨架（响应规范、异常基类、错误码体系、配置约定）
- **运行时**：要求 JDK 21+；构建工具 Maven 3.9+
