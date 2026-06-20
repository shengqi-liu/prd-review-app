## ADDED Requirements

### Requirement: AiService 接口
系统 SHALL 在 application 层定义 `AiService` 接口，提供以下方法：
- `summarizeFromUrl(String url)`：读取 URL 内容并摘要，返回 `SummarizeResult{title, content}`
- `summarizeText(String rawText)`：对已有文本摘要，返回 `SummarizeResult{title, content}`

实现类 `AiServiceImpl` SHALL 位于 infrastructure 层，使用 Spring AI `ChatClient` 调用 Claude API。

#### Scenario: URL 摘要成功
- **WHEN** 调用 `summarizeFromUrl("https://internal.wiki/prd-doc")`，URL 可正常访问
- **THEN** 返回 `SummarizeResult`，`title` MUST 非空，`content` MUST 包含文档核心内容摘要，字数 MUST 在 100–2000 字之间

#### Scenario: summarizeText 成功
- **WHEN** 调用 `summarizeText(rawText)`，rawText 长度 > 50 字
- **THEN** 返回非空 `SummarizeResult`，title 和 content 均 MUST 非空

---

### Requirement: URL 文档获取
系统 SHALL 使用 `WebClient` 服务端拉取 URL 内容，支持 HTML 和纯文本两种 Content-Type。

- HTML 内容 SHALL 使用 Jsoup 提取 `<body>` 正文，去除脚本和样式标签
- 纯文本或 Markdown 直接使用原始内容
- 连接超时 MUST 为 5 秒，读取超时 MUST 为 15 秒

#### Scenario: HTML 页面正文提取
- **WHEN** URL 返回 `Content-Type: text/html`
- **THEN** 系统 MUST 使用 Jsoup 提取正文文本，结果 MUST NOT 包含 HTML 标签

#### Scenario: 纯文本直通
- **WHEN** URL 返回 `Content-Type: text/plain` 或 `text/markdown`
- **THEN** 系统 MUST 直接使用原始文本内容，不做 HTML 解析

#### Scenario: URL 连接超时
- **WHEN** URL 在 5 秒内无响应
- **THEN** 系统 MUST 抛出 `AiServiceException`，消息包含 "URL 读取超时"

#### Scenario: URL 返回非 2xx 状态码
- **WHEN** URL 返回 4xx 或 5xx 响应
- **THEN** 系统 MUST 抛出 `AiServiceException`，消息包含 HTTP 状态码

---

### Requirement: Claude API 调用
系统 SHALL 通过 Spring AI `ChatClient` 调用 Claude API（HS256 API Key 鉴权），模型默认为 `claude-sonnet-4-5`，可通过环境变量 `AI_MODEL` 覆盖。

- API Key MUST 通过环境变量 `ANTHROPIC_API_KEY` 注入，禁止硬编码
- 单次调用 max-tokens MUST 默认为 1024，temperature MUST 默认为 0.3
- Claude API 调用超时 MUST 为 30 秒

#### Scenario: 摘要 Prompt 输出结构化 JSON
- **WHEN** `AiServiceImpl` 调用 Claude 摘要文档
- **THEN** Prompt MUST 要求 Claude 输出严格的 JSON 格式：`{"title":"...","content":"..."}`，系统 MUST 能解析该 JSON 填充 `SummarizeResult`

#### Scenario: Claude 返回非 JSON 时的回退
- **WHEN** Claude 返回非 JSON 格式（极端情况）
- **THEN** 系统 MUST 启用回退策略：title 取 URL 域名，content 取原始文本前 500 字，并记录 WARN 日志

#### Scenario: API Key 未配置
- **WHEN** `ANTHROPIC_API_KEY` 环境变量未设置
- **THEN** 应用启动 MUST 失败，日志 MUST 输出明确错误提示

---

### Requirement: AiServiceException
系统 SHALL 定义 `AiServiceException extends RuntimeException`，在 AI 调用链路任意环节失败时抛出。`GlobalExceptionHandler` MUST 捕获该异常，返回 `{"code":99997,"message":"AI 服务调用失败"}`。

#### Scenario: AiServiceException 被全局捕获
- **WHEN** `AiServiceImpl` 抛出 `AiServiceException`
- **THEN** `GlobalExceptionHandler` MUST 返回 `{"code":99997,"message":"AI 服务调用失败"}`，HTTP 200

---

### Requirement: SseEventEmitter 工具类
系统 SHALL 提供 `SseEventEmitter` 工具类，封装 Spring MVC `SseEmitter`（超时 60 秒），提供类型化阶段事件推送方法。

事件 JSON 格式 SHALL 统一为：
```json
{"stage":"<stage>","message":"<message>","data":<data_or_null>}
```

支持的 stage 值：`fetching`、`summarizing`、`done`、`error`。

#### Scenario: 阶段事件顺序推送
- **WHEN** 依次调用 `sendFetching()`、`sendSummarizing()`、`sendDone(data)`
- **THEN** 客户端 SSE 连接 MUST 依次收到三条事件，stage 值依次为 fetching / summarizing / done

#### Scenario: 错误事件推送后连接关闭
- **WHEN** 调用 `sendError(message)`
- **THEN** 系统 MUST 推送 stage=error 事件，并调用 `SseEmitter.complete()` 关闭连接

#### Scenario: done 事件携带 data
- **WHEN** 调用 `sendDone(prdResponse)`
- **THEN** 事件 JSON 的 `data` 字段 MUST 包含 prdResponse 的完整 JSON 序列化内容

---

### Requirement: AiProperties 配置类
系统 SHALL 提供 `AiProperties`（`@ConfigurationProperties(prefix = "ai")`），读取以下配置：

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `ai.fetch.timeout-connect-ms` | 5000 | WebClient 连接超时（毫秒） |
| `ai.fetch.timeout-read-ms` | 15000 | WebClient 读取超时（毫秒） |
| `ai.fetch.user-agent` | `PrdReview-Bot/1.0` | HTTP User-Agent |

#### Scenario: 配置读取成功
- **WHEN** 应用启动，`application.yml` 配置了 `ai.fetch.*`
- **THEN** `AiProperties` Bean MUST 正确注入对应值，`AiServiceImpl` MUST 使用该配置创建 `WebClient`
