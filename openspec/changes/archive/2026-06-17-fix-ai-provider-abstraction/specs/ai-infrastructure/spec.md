## RENAMED Requirements

- FROM: `### Requirement: Claude API 调用`
- TO: `### Requirement: LLM API 调用`

## MODIFIED Requirements

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
