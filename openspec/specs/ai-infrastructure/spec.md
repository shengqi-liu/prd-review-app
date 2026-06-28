# AI Infrastructure

## Purpose

AI 评审系统的底层调用基础设施：定义 `AiService` 接口屏蔽 Spring AI / Provider 细节，提供 URL 文档获取与摘要、LLM API 调用（provider 中立，由激活的 starter 决定）、SSE 流式事件推送、统一异常处理等横切能力，供 PRD / Reviewer / Review 等上层 capability 复用。

## Requirements

### Requirement: AiService 接口
系统 SHALL 在 application 层定义 `AiService` 接口，提供以下方法：
- `summarizeFromUrl(String url)`：读取 URL 内容并摘要，返回 `SummarizeResult{title, content}`
- `summarizeText(String rawText)`：对已有文本摘要，返回 `SummarizeResult{title, content}`

实现类 `AiServiceImpl` SHALL 位于 infrastructure 层，使用 Spring AI `ChatClient` 调用 LLM API（具体 provider 由激活的 starter 决定，详见"LLM API 调用"Requirement）。

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

### Requirement: LLM API 调用
系统 SHALL 通过 Spring AI `ChatClient` 调用 LLM API。具体使用的 LLM provider 由部署时激活的 `spring-ai-starter-model-*` 依赖决定（OpenAI / Anthropic / ZhipuAI / Ollama 等），业务代码 SHALL 不感知 provider 差异。

约束：
- API Key MUST 通过环境变量注入（具体环境变量名由激活的 provider 决定，如 `OPENAI_API_KEY`、`ANTHROPIC_API_KEY`），禁止硬编码
- 单次调用 max-tokens MUST 默认为 1024，temperature MUST 默认为 0.3
- LLM API 调用超时 MUST 为 30 秒
- 模型名称 MUST 可通过环境变量 `AI_MODEL` 覆盖
- 对于 OpenAI 兼容协议的 provider（如 DeepSeek），SHALL 支持通过 `base-url` 配置项指向第三方端点

当前默认部署配置：OpenAI 兼容协议指向 DeepSeek（`base-url=https://api.deepseek.com`，`model=deepseek-chat`）。切换其他 provider 只需替换 starter 依赖 + 改对应 yaml 配置段，无需改动业务代码。

#### Scenario: 摘要 Prompt 输出结构化 JSON
- **WHEN** `AiServiceImpl` 调用 LLM 摘要文档
- **THEN** Prompt MUST 要求 LLM 输出严格的 JSON 格式：`{"title":"...","content":"..."}`，系统 MUST 能解析该 JSON 填充 `SummarizeResult`

#### Scenario: LLM 返回非 JSON 时的回退
- **WHEN** LLM 返回非 JSON 格式（极端情况）
- **THEN** 系统 MUST 启用回退策略：title 取原文前 20 字 + "..."，content 取原始文本前 500 字 + "..."，并记录 WARN 日志

#### Scenario: API Key 未配置
- **WHEN** 激活的 provider 对应的 API Key 环境变量未设置（例如 OpenAI starter 激活但缺 `OPENAI_API_KEY`）
- **THEN** 应用启动 MUST 失败，日志 MUST 输出明确错误提示

#### Scenario: 切换 provider 仅需替换 starter + 配置
- **WHEN** 把 `spring-ai-starter-model-openai` 换成 `spring-ai-starter-model-anthropic` 并同步替换 `spring.ai.openai.*` 为 `spring.ai.anthropic.*`
- **THEN** 业务代码（`AiServiceImpl` 等）MUST 不需要任何改动即可正常运行

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

---

### Requirement: AI 摘要服务（AiService）
系统 SHALL 提供 `AiService` 接口暴露 AI 摘要能力，屏蔽底层 Spring AI 与 LLM provider 细节。接口包含：

- `summarizeFromUrl(String url)` — 抓取 URL 内容后摘要（#4.5 已实现）
- `summarizeText(String rawText)` — 对已有文本摘要（#4.5 已实现）
- `streamCompletion(String prompt)` / `streamCompletion(String system, String user)` — 流式补全（#9 / fix-kb-sync-correctness 实现）
- `summarizeFromFile(byte[] bytes, String filename)` — 解析文件后摘要（#7 新增）

`summarizeFromFile` 的实现 SHALL：
1. 用 Tika `AutoDetectParser` 基于内容 + 文件名检测 MIME 类型
2. 若 MIME 不在白名单（PDF / DOCX / DOC / TXT / MD），抛 `BizException(PRD_FILE_TYPE_UNSUPPORTED)`
3. 用对应 parser 解析为纯文本；若解析失败或结果空白/过短，抛 `BizException(PRD_FILE_PARSE_FAILED)`
4. 调 `summarizeText(parsedText)` 复用现有 AI 摘要流程，返回 `SummarizeResult`

#### Scenario: summarizeFromFile 解析 PDF 后调用 summarizeText
- **WHEN** 传入合法 PDF 字节流
- **THEN** Tika MUST 检测为 `application/pdf`、解析为纯文本，并调 `summarizeText` 返回带 title/content 的 `SummarizeResult`

#### Scenario: summarizeFromFile 拒绝白名单外类型
- **WHEN** 传入 `.zip` 文件字节流
- **THEN** MUST 抛 `BizException(PRD_FILE_TYPE_UNSUPPORTED)`，不调用 ChatClient

#### Scenario: summarizeFromFile 文本过短抛失败
- **WHEN** Tika 解析后得到 < 10 字符的纯文本（疑似扫描件 PDF）
- **THEN** MUST 抛 `BizException(PRD_FILE_PARSE_FAILED)`，错误消息提示"可能是扫描件"

#### Scenario: summarizeFromFile 与 summarizeFromUrl 行为对称
- **WHEN** 同一份内容通过文件上传和 URL 抓取分别调用
- **THEN** 两者返回的 `SummarizeResult` MUST 是等价的（同一份原始文本走相同的 summarizeText 流程）
