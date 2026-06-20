## Why

#4.5 `add-ai-infrastructure` 当时落地的代码本就是 provider 中立的（Spring AI `ChatClient` 抽象 + starter 替换即可切换 provider），但 spec 写得过细，把"Claude API"、`ANTHROPIC_API_KEY`、`claude-sonnet-4-5` 模型名等具体 provider 细节当成了系统约束。

#9 实施期间用户用真实 key 调通，因为 Claude 没有可用 key，临时把 starter 切到 OpenAI 协议指向 DeepSeek——代码完全不动就跑通了，证明设计是对的；但 spec 现在和实现不一致，也错误地把"未来 provider 锁死成 Claude"写进了系统契约。

本 change 只修 spec，不动代码。把 provider 提到抽象层："系统通过 Spring AI ChatClient 调用 LLM API，具体 provider 由激活的 starter 决定"。

## What Changes

- **MODIFIED Requirement: Claude API 调用 → LLM API 调用**：从"必须用 Claude"改为"通过激活的 Spring AI starter（OpenAI / Anthropic / ZhipuAI / Ollama 等）调用对应 provider"；环境变量名由 provider 决定（`OPENAI_API_KEY` / `ANTHROPIC_API_KEY` 等）；默认 provider 改为 OpenAI 协议指向 DeepSeek（与当前实现一致）
- **MODIFIED Requirement: AiServiceException 描述微调**：把"AI 调用失败"语义保持不变，但去掉与 Claude 强绑定的暗示
- 重命名：把 "Claude API 调用" Requirement 改名为 "LLM API 调用"（RENAMED Requirements 段）
- **不改任何代码**：当前 `AiServiceImpl`、`AiProperties`、`pom.xml`、`application-dev.yml` 已经符合新 spec

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `ai-infrastructure`: 把"Claude API 调用" Requirement 改名 + 改写为 provider 抽象描述

## Impact

- **代码**：无
- **配置**：无（当前 application-dev.yml 已经是 OpenAI 协议指向 DeepSeek 的格式）
- **数据库**：无
- **运维文档**：建议同步更新 README / 部署手册，把"配置 ANTHROPIC_API_KEY"改成"按激活的 starter 配置对应环境变量"——这部分不在本 change scope 内
