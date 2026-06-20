## Context

#4.5 落地时项目方向是"用 Claude 评审 PRD"，于是把 Anthropic 写进了 spec。但代码实现走的是 Spring AI `ChatClient` 抽象——通过替换 `spring-ai-starter-model-*` 依赖 + 改 yaml 配置即可切换 provider，不需要改一行业务代码。

#9 实施时用户用 DeepSeek 真实 key 验证流式输出，因 Anthropic 无可用 key，只把 starter 从 `spring-ai-starter-model-anthropic` 换成 `spring-ai-starter-model-openai`、把 yaml 里的 `spring.ai.anthropic.*` 段换成 `spring.ai.openai.*`（含 `base-url: https://api.deepseek.com`），整套链路立刻跑通——验证了"provider 中立"的设计本就成立。

但 spec 现在反过来错了：它把 Claude 当成系统的"硬约束"，未来如果有人按 spec 实现就会做出 Claude 专属代码。本 change 把 spec 拉回到与实现一致的抽象高度。

## Goals / Non-Goals

**Goals:**

- spec 描述与实际代码一致：provider 中立，由 starter + yaml 配置决定
- 保留 #4.5 已有的所有不变量（API Key 不硬编码、超时约束、JSON 输出契约、回退策略）
- 留好后续切换 provider 的扩展点：环境变量命名 / 模型名 / base-url 都参数化

**Non-Goals:**

- 不改代码、不改配置（当前已经是正确状态）
- 不重新设计 provider 抽象层（Spring AI 已经做了，不重复发明）
- 不引入 provider 选择策略（同一部署只激活一个 starter，足够）

## Decisions

### D1: Requirement 重命名 + 改写，而非删除+新增

**选择**：用 `## RENAMED Requirements` + `## MODIFIED Requirements` 同时表达"改名"和"改内容"。

**理由**：
- 保留语义连续性——这本来就是同一条 Requirement（LLM 调用），只是描述抽象层次错了
- 删除+新增会丢失 Scenario 的对应关系，归档时主 spec 难合并
- OpenSpec delta 操作支持 RENAMED + MODIFIED 组合

### D2: 默认 provider 在 spec 中写"由 starter 决定"，不写具体名称

**选择**：spec 只说"系统支持任何 Spring AI 兼容 provider，默认激活的 starter 由部署配置决定；当前默认为 OpenAI 协议指向 DeepSeek"。

**理由**：
- 描述事实状态（当前默认 DeepSeek）但不把它写成系统约束（未来可能改成 Claude / Ollama）
- "由 starter 决定"才是真正的不变量，具体 provider 是可变的配置项
- 避免下次切换 provider 时又得改 spec

### D3: API Key 校验从"必须配置 ANTHROPIC_API_KEY"改为"必须配置激活 provider 对应的 API Key"

**选择**：把 Scenario "API Key 未配置" 的具体环境变量名换成通用描述。

**理由**：
- Spring AI 各 provider starter 都会在缺 key 时启动失败，行为本就一致
- 写具体 `OPENAI_API_KEY` 又会和 D2 矛盾（变成 OpenAI 锁死）
- 用"激活 provider 对应的 API Key"既准确又不锁死

## Risks / Trade-offs

- **[抽象描述可能让新人困惑：到底用什么 provider？]** → README / 部署手册需要补一段"当前默认 provider 和配置示例"。Mitigation：design.md 里明确写出当前默认（DeepSeek via OpenAI 协议），运维文档另起 task 跟进。
- **[Scenario "Claude 返回非 JSON 时的回退" 用了 Claude 字样]** → 改成"LLM 返回非 JSON 时的回退"。Mitigation：本 change 顺手改掉。
- **[未来引入 review_style / kb_context 注入时还会动到这块吗？]** → 不会。那是 Prompt Composer（#15）的事，与 provider 抽象无关。

## Migration Plan

无代码迁移。spec 修改后归档同步进主 spec，应用层运行时行为零变化。
