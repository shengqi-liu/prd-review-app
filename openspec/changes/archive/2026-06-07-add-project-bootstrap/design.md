## Context

AI 产品方案评审系统的整体路线图包含 24 个 OpenSpec change，覆盖鉴权、PRD 管理、AI 评审员、评审风格、知识库 RAG、评审编排、报告归档等多个能力。后续 23 个 change 都会读写代码、产出 REST 接口、抛业务异常、记录日志、暴露配置项。

如果没有在第一个 change 把这些"横切关注点"统一掉，后续每个 change 都会自己造一套响应包装、错误码、日志格式，导致：
- 接口风格不一致，前端难以统一处理
- 业务异常分类混乱，错误排查成本高
- 日志格式各异，无法对接统一日志平台
- 配置项散落，运维难以管理

本 change 的核心目标是**一次性把工程地基打稳**，让后续 change 只需要关注业务逻辑，不需要反复关心基础设施。

**项目当前状态**：根目录除 `prototype/`（前端原型）和 `openspec/`（规格目录）外没有任何 Java 工程文件。本 change 完成后将出现完整的 Maven 多模块 Java 项目。

**约束条件**：
- 必须使用 Java 21 + Maven（业务方明确要求）
- 必须使用 Spring Boot 3.x（与后续 Spring AI 1.0 兼容）
- 必须支持虚拟线程（后续多 Agent 并行评审依赖此特性）
- 团队对 Spring 生态熟悉，避免引入 Quarkus / Micronaut 等额外学习成本

## Goals / Non-Goals

**Goals:**
- 落地 Maven 多模块结构与单向依赖约束
- 落地 `Result<T>` 统一响应包装与 `ErrorCode` 错误码体系
- 落地全局异常处理与 `BizException` 业务异常基类
- 落地 Logback JSON 日志 + traceId 链路追踪
- 落地多 profile 配置规范（dev / prod 分离）
- 接入 OpenAPI 文档与 Actuator 健康检查
- 提供一个最小可运行的 `/api/v1/ping` 端点验证骨架
- 建立 `openspec/roadmap.md` 路线图作为后续 change 的导航地图

**Non-Goals:**
- 数据库连接（留给 `add-prd-storage` change 处理，避免本 change 引入过多依赖）
- 用户登录鉴权（留给 `add-auth-foundation`）
- Docker Compose 基础设施编排（留给 `add-infra-compose`）
- 任何业务领域代码（PRD、Reviewer、评审等全部留给后续 change）
- CI/CD 流水线配置（与代码骨架解耦，可后续单独 change 处理）
- 国际化 / 多语言支持（一期仅中文场景）

## Decisions

### 决策 1：Maven 多模块组织 — DDD 分层 + 包级别上下文（方案 B）

**选择**：5 模块 DDD 分层：`api` / `application` / `domain` / `infrastructure` / `bootstrap`，模块内部通过包路径 `com.prdreview.<context>.*` 区分 bounded context。

**模块职责**：
| 模块 | 职责 | 典型内容 |
|------|------|---------|
| `api` | 接口层 | `@RestController`、对外 DTO（Request/Response）、`Result<T>` 包装、`ErrorCode` 枚举、`BizException`、全局异常处理 |
| `application` | 应用层 | Application Service（用例编排）、事务边界、领域事件订阅者、DTO ↔ 领域对象转换 |
| `domain` | 领域层 | 聚合根、实体、值对象、领域服务、Repository 接口、领域事件 |
| `infrastructure` | 基础设施层 | Repository 实现（JPA / MyBatis）、外部服务客户端（Claude API、Chroma）、持久化映射 |
| `bootstrap` | 启动层 | `@SpringBootApplication` 主类、Web 配置、Filter 注册、全局 Bean 装配 |

**包级别 Bounded Context 约定**：
```
com.prdreview.<context>.<layer-element>

示例：
  com.prdreview.prd.controller     (在 api 模块)
  com.prdreview.prd.service        (在 application 模块)
  com.prdreview.prd.domain         (在 domain 模块)
  com.prdreview.prd.repository     (在 infrastructure 模块)
```

7 个 bounded context（对应路线图的 7 个 capability）：`auth`、`prd`、`reviewer`、`reviewstyle`、`knowledgebase`、`review`、`common`（横切）。

**理由**：
- 业务方倾向方案 B（务实派）：模块数量可控（5 个），改动温和
- DDD 分层模型清晰，团队对"接口 → 应用 → 领域 ← 基础设施"的心智模型熟悉
- 包级别区分上下文，未来拆分微服务时可低成本平移（每个 context 的包路径迁移到独立服务即可）
- Maven 模块强约束依赖方向，避免越层调用
- `api` 模块独立可作为 SDK 输出，前端可基于 OpenAPI 文档生成 TypeScript 客户端

**替代方案**：
- 方案 A（每个 context 独立 Maven 模块 × 4 层 = 28 个模块）：上下文边界更强，但 bootstrap 阶段空模块过多，过度设计
- 方案 C（context 内分层 Maven 模块）：折中方案，但模块数量仍达 18+
- 4 模块（合并 application 进 api）：简单但混淆"接口适配"与"用例编排"两种职责

**包级别上下文的局限与缓解**：
- **局限**：包结构靠开发自觉，程序员可跨包引用导致上下文渗透
- **缓解 1**：在 `domain` 模块通过 ArchUnit 编写架构测试，断言"上下文 A 的包 MUST NOT 引用上下文 B 的包"
- **缓解 2**：跨上下文通信统一走 `application` 层的事件发布订阅（`ApplicationEventPublisher`）
- **缓解 3**：代码审查清单中列入"上下文边界检查项"

**模块依赖关系**：
```
        bootstrap
           ↓
          api ──────┐
           ↓        ↓
       application  │
           ↓        ↓
         domain ←───┘
           ↑
     infrastructure
```

> 注：`api` 模块也可依赖 `domain`（用于 DTO ↔ 领域对象转换契约），但禁止依赖 `infrastructure`。

### 决策 2：响应包装 - 包装所有响应 vs 仅包装业务响应

**选择**：包装所有业务接口响应（`/api/**`），不包装 Spring 自身端点（`/actuator/**`、`/swagger-ui/**`、`/v3/api-docs`）

**理由**：
- 业务接口统一格式，前端拦截器一处处理 `code !== 0` 的情况
- Spring 端点保留原始格式，便于第三方工具直接消费

**实现方式**：通过 `ResponseBodyAdvice` 自动包装，控制器代码直接 `return user`，无需手动 `Result.success(user)`，降低使用成本。

### 决策 3：HTTP 状态码与业务错误码的关系

**选择**：业务异常一律 HTTP 200，错误信息由 `code` 字段表达；系统级异常保留 5xx

**理由**：
- 前端只需要判断 `code === 0`，无需在 HTTP 状态码和响应体之间反复切换
- 监控告警体系仍能从 5xx 准确捕获系统级问题
- 与字节内部主流 Java 项目约定一致，降低团队心智成本

**替代方案**：
- 严格 REST：4xx 表达业务错误（如 404 资源不存在）— 但前端处理成本高，且不适合统一拦截
- 全部 200 + code 标识：放弃了 5xx 的系统级告警能力

### 决策 4：错误码编排方式

**选择**：5 位整数分段编码

**理由**：
- 编排清晰，按段位即可定位错误来源域
- 5 位足以覆盖每个域 9999 个错误码
- 整数错误码易于在日志、监控、告警系统中聚合

**分段表**：
| 段位 | 用途 |
|------|------|
| 0 | 成功 |
| 10000–19999 | 通用错误（参数校验、找不到资源等） |
| 20000–29999 | 鉴权与权限 |
| 30000–39999 | PRD 域 |
| 40000–49999 | 评审域 |
| 50000–59999 | 知识库域 |
| 60000–69999 | Reviewer / Style 域 |
| 90000–99999 | 系统级（数据库、外部服务、未知异常） |

### 决策 5：日志格式 - 纯文本 vs JSON

**选择**：JSON（通过 `logstash-logback-encoder`）

**理由**：
- 便于对接 ELK / Loki 等结构化日志平台
- traceId、userId 等字段可作为独立字段索引
- 本地开发时可通过 IDE 插件或 `jq` 美化展示

**实现**：
- `dev` profile 输出彩色文本（控制台友好）
- `prod` profile 输出 JSON（结构化采集）

### 决策 6：traceId 注入位置

**选择**：Servlet Filter（最外层），优先级 `HIGHEST_PRECEDENCE`

**理由**：
- Filter 早于 Controller、Interceptor 执行，保证全链路覆盖
- MDC 写入后整个请求生命周期可见，包括异步线程（虚拟线程下 MDC 自动传播）
- 响应返回时通过相同 Filter 在响应头 `X-Trace-Id` 中暴露

**注意**：虚拟线程下 MDC 仍可用（Spring Boot 3.2+ 已适配），无需额外配置。

### 决策 7：OpenAPI 文档接入方式

**选择**：springdoc-openapi（基于 Swagger 3）

**理由**：
- Spring Boot 3.x 原生支持，零配置自动扫描控制器
- 注解 `@Operation` / `@Schema` 与代码一体维护
- 生态成熟，前端可基于 `/v3/api-docs` 自动生成 TypeScript 客户端

**替代方案**：
- springfox：已停止维护，不兼容 Spring Boot 3
- 手写 OpenAPI YAML：维护成本高，易与代码脱节

### 决策 8：路线图文档格式

**选择**：单一 Markdown 文件 `openspec/roadmap.md`

**理由**：
- 与 OpenSpec 文档体系一致
- 可在 Git 中追踪状态变更
- 简单纯文本，无需额外工具

**内容结构**：阶段分组 + 表格（Change ID / 名称 / 依赖 / 状态 / 完成时间）+ 依赖关系示意图。

## Risks / Trade-offs

- **【风险】Maven 多模块对初期开发效率有影响** → 缓解：第一个 change 即把模块拆好并提供示例代码，后续 change 沿用模式即可，长期收益大于短期成本
- **【风险】业务异常一律 HTTP 200 不符合严格 REST 风格，对外开放 API 时可能引发争议** → 缓解：此为内部系统，统一约定即可；如未来需对外开放，可通过 API 网关在边界层做协议转换
- **【风险】JSON 日志在本地开发时阅读困难** → 缓解：`dev` profile 输出彩色文本，仅 `prod` 启用 JSON
- **【风险】springdoc 在虚拟线程下偶有兼容性问题（社区已知）** → 缓解：固定使用经过验证的 2.3.0+ 版本，CI 中加入 Swagger UI 可用性测试
- **【权衡】不引入数据库会让本 change 看起来"过于简单"，但保持小颗粒度更符合 OpenSpec 演进思路** → 接受：数据库与 PRD 模块在 `add-prd-storage` 一起落地，本 change 仅交付骨架

## Open Questions

- 是否需要在本 change 引入 `spring-boot-starter-security` 占位（即使先全部 `permitAll()`）？倾向于不引入，留给 `add-auth-foundation` 时一起设计
- 是否需要在 `pom.xml` 引入 Maven Enforcer 插件强制模块依赖方向？倾向于本 change 落地，避免后续 change 误引依赖
