## 1. Spec 修订

- [x] 1.1 在 `openspec/specs/ai-infrastructure/spec.md` 中把 "Requirement: Claude API 调用" 改名为 "Requirement: LLM API 调用"
- [x] 1.2 重写该 Requirement 的描述：从"Claude 专属"改为"provider 中立 + 通过 Spring AI starter 切换"，按 delta spec 的内容替换
- [x] 1.3 把 "Scenario: Claude 返回非 JSON 时的回退" 改为 "Scenario: LLM 返回非 JSON 时的回退"，并校正回退策略描述（之前写的 "title 取 URL 域名" 实际代码取的是原文前 20 字 + "..."，spec 已不准确，同步修正）
- [x] 1.4 把 "Scenario: API Key 未配置" 的描述从绑死 `ANTHROPIC_API_KEY` 改为"激活 provider 对应的 API Key"
- [x] 1.5 新增 "Scenario: 切换 provider 仅需替换 starter + 配置"，表达 provider 中立的不变量
- [x] 1.6 顺手修正 `reviewer-testing/spec.md` 中遗留的 "Claude API 调用" 措辞（Purpose + Scenario）

## 2. 代码与配置（仅校对，理论上无改动）

- [x] 2.1 校对 `AiServiceImpl.java`：把 `CLAUDE_TIMEOUT_SECONDS` 常量改名 `LLM_TIMEOUT_SECONDS`；测试 DisplayName 中的"Claude 返回"批量改为"LLM 返回"
- [x] 2.2 校对 `application-prod.yml`：增加 OpenAI 协议示例（与 dev 一致），保留 Anthropic 示例作为注释
- [x] 2.3 校对 `PrdReviewApplicationTests`：把 `spring.ai.anthropic.api-key=sk-ant-test` 改为 `spring.ai.openai.api-key=sk-test`，与激活的 starter 对齐
- [x] 2.4 校对 `README.md` 和 `roadmap.md` 的技术栈描述：从"Spring AI 1.0 + Claude API"改为"Spring AI 1.0（provider 中立）"

## 3. 路线图与归档

- [x] 3.1 实现完成后，由 `/opsx:archive` 自动同步主 spec、归档 change；roadmap.md 不需要改（这是 spec 修订，非新功能）
