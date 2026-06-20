## 1. 依赖引入

- [x] 1.1 在 parent `pom.xml` 的 `<dependencyManagement>` 中引入 `spring-ai-bom:1.0.0`（import scope）
- [x] 1.2 在 `infrastructure/pom.xml` 添加默认 AI Provider starter（默认 `spring-ai-anthropic-spring-boot-starter`，无需版本号，由 BOM 管理；切换其他 provider 只需替换此依赖）+ `spring-boot-starter-webflux`（WebClient）
- [x] 1.3 在 `infrastructure/pom.xml` 添加 `jsoup:1.17.2`（HTML 正文提取）
- [x] 1.4 在 `bootstrap/pom.xml` 添加与 1.2 相同的 AI Provider starter（触发 AutoConfiguration）

## 2. 配置文件

- [x] 2.1 在 `bootstrap/src/main/resources/application.yml` 新增通用 `ai.fetch.*` 配置（`timeout-connect-ms: 5000`、`timeout-read-ms: 15000`、`user-agent: PrdReview-Bot/1.0`）；不在此文件写 `spring.ai.*`（provider 专属配置放到 profile 文件）
- [x] 2.2 在 `application-dev.yml` 新增默认 provider（Anthropic）的 `spring.ai.anthropic.*` 配置段，同时以注释形式提供 ZhipuAI / Ollama 的切换示例，说明本地需 `export ANTHROPIC_API_KEY=sk-ant-...`（或对应 provider 的环境变量）
- [x] 2.3 在 `application-prod.yml` 预留 `spring.ai.*` provider 配置占位注释，提示运维按部署环境填写

## 3. Application 层 — AiService 接口

- [x] 3.1 创建 `application/.../ai/service/AiService.java` 接口（`summarizeFromUrl(String url)`、`summarizeText(String rawText)`）
- [x] 3.2 创建 `application/.../ai/dto/SummarizeResult.java` record（`String title`、`String content`）

## 4. Infrastructure 层 — AiServiceImpl

- [x] 4.1 创建 `infrastructure/.../ai/AiProperties.java`（`@ConfigurationProperties(prefix = "ai")`，含 Fetch 内嵌类，读取连接超时、读取超时、User-Agent）
- [x] 4.2 创建 `infrastructure/.../ai/DocumentFetcher.java`：
  - 使用 `WebClient`（基于 `AiProperties` 配置超时和 User-Agent）
  - `fetchContent(String url)` → 返回原始字符串
  - 根据 Content-Type 决定是否调用 Jsoup 提取正文
  - 非 2xx 或超时时抛出 `AiServiceException`
- [x] 4.3 创建 `infrastructure/.../ai/AiServiceImpl.java` 实现 `AiService`：
  - 注入 Spring AI `ChatClient`
  - `summarizeFromUrl(url)`：调用 `DocumentFetcher.fetchContent(url)` → 调用 `summarizeText(rawText)`
  - `summarizeText(rawText)`：构建摘要 Prompt，调用 `ChatClient.prompt(…).call().content()`，解析 JSON 返回 `SummarizeResult`
  - JSON 解析失败时触发回退策略（title=原文前 20 字，content=前 500 字），记录 WARN 日志
  - Claude 调用超时时抛出 `AiServiceException`

## 5. 异常类

- [x] 5.1 创建 `domain/.../common/exception/AiServiceException.java`（继承 `RuntimeException`，含 message + optional cause；放 domain 包以便 api 层捕获）
- [x] 5.2 在 `GlobalExceptionHandler` 中捕获 `AiServiceException`，返回 `Result.error(ErrorCode.AI_SERVICE_ERROR)`

## 6. SSE 工具

- [x] 6.1 创建 `api/.../common/sse/SseEventEmitter.java`：
  - 内部持有 `SseEmitter`（超时 60000ms）
  - `sendFetching(String message)`
  - `sendSummarizing(String message)`
  - `sendDone(Object data)`：序列化 data 为 JSON，推送 stage=done
  - `sendError(String message)`：推送 stage=error，调用 `emitter.complete()`
  - `complete()`：显式完成
  - `getEmitter()`：返回 `SseEmitter` 供 Controller 返回给 Spring MVC
- [x] 6.2 SSE 事件对象 `SseEvent.java`（`record`：`String stage`、`String message`、`Object data`），序列化时 `data` 为 null 则省略

## 7. 测试

- [x] 7.1 `AiServiceImplTest`（单元测试，Mock `ChatClient` 和 `DocumentFetcher`）：
  - `summarizeText` 正常返回解析 JSON
  - `summarizeText` Claude 返回非 JSON 时触发回退
- [x] 7.2 `DocumentFetcherTest`（MockWebServer 集成测试）：
  - HTML Content-Type → Jsoup 提取正文，不含 HTML 标签
  - 纯文本 Content-Type → 原文直通
  - 非 2xx 状态码 → 抛出 `AiServiceException`
- [x] 7.3 `SseEventEmitterTest`（单元测试）：
  - 依次推送 fetching / summarizing / done，验证事件 JSON stage 字段
  - data 为 null 时序列化省略 data 字段
- [x] 7.4 `AiPropertiesTest`（`@SpringBootTest` 加载配置）：
  - 验证默认值正确绑定（5000 / 15000 / PrdReview-Bot/1.0）
