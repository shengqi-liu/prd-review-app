## Why

change#5 `add-prd-storage` 需要通过 URL 读取内部文档并由 AI 摘要生成 PRD title/content。后续 change#15（Prompt 拼装）、change#17（内审）、change#18（多 Agent 并行评审）均需要调用 Claude API。

为避免每个 change 各自实现一份 AI 调用代码，将 AI 能力抽为可复用的基础设施层，统一管理：Spring AI 配置、MCP fetch、SSE 流式输出、超时/重试策略。

## What Changes

- **新增** `AiService` 接口（application 层或 domain 层）：`summarizeFromUrl(url)` 返回 `{title, content}`
- **新增** `AiServiceImpl`（infrastructure 层）：Spring AI Anthropic 调用 + MCP fetch 工具
- **新增** SSE 工具封装：`SseHelper`，统一推送阶段事件格式（fetching / summarizing / done / error）
- **新增** Spring AI 依赖配置（bootstrap pom + application.yml）
- **新增** Claude API Key 环境变量注入（`ANTHROPIC_API_KEY`）
- **新增** `AiProperties` 配置类（model、timeout、maxTokens）

## Capabilities

### New Capabilities

- `ai-infrastructure`: AiService 接口 + Spring AI Anthropic 实现 + MCP document fetch + SSE 流式工具

### Modified Capabilities

（无）

## Impact

- **新增依赖**：`spring-ai-anthropic-spring-boot-starter`、`spring-ai-mcp-client`（或自定义 MCP fetch）
- **环境变量**：新增 `ANTHROPIC_API_KEY`（必填），`AI_MODEL`（可选，默认 claude-sonnet-4-5）
- **配置文件**：`application-dev.yml`、`application-prod.yml` 新增 `spring.ai.*` 配置段
- **下游依赖**：change#5（prd-storage）、change#15（prompt-composer）、change#17（internal-review）
