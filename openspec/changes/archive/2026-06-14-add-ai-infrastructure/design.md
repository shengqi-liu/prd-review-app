## Context

项目技术栈已声明 Spring AI 1.0，`application-dev.yml` 中预留了 `spring.ai.vectorstore.chroma` 配置段，但 pom.xml 尚未引入任何 Spring AI 依赖。

本 change 的直接使用方是 change#5（PRD URL 摘要），后续 change#15（Prompt 拼装）、#17（内审 SSE）、#18（多 Agent 并行）均需复用 AI 调用能力。

## Goals / Non-Goals

**Goals:**
- 引入 Spring AI Anthropic 依赖，统一管理 Claude API 调用
- 提供 `AiService` 接口（application 层），屏蔽底层 Spring AI 细节
- 提供 URL 文档读取 → AI 摘要的完整链路（`summarizeFromUrl`）
- 提供 SSE 阶段事件推送工具（`SseEventEmitter`），供 change#5 及后续使用
- 统一 AI 超时、重试、错误处理策略

**Non-Goals:**
- Chroma 向量库集成（change#12）
- Embedding 生成（change#12）
- 流式 token 输出给前端（change#17 才需要逐 token SSE）
- 多 Agent 并发调度（change#18）

## Decisions

### D1: Spring AI 版本选择 — 1.0.0（Provider 可配置）

Spring AI 1.0.0 GA 兼容 Spring Boot 3.2.x，使用 `spring-ai-bom` 统一管理版本。  
`ChatClient` 是 Spring AI 的核心抽象，屏蔽底层 provider 细节。**切换 AI 服务商只需替换 starter 依赖和配置文件，`AiServiceImpl` 代码无需修改。**

```xml
<!-- parent pom — dependencyManagement -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bom</artifactId>
    <version>1.0.0</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

**支持的 Provider（任选其一引入 starter）：**

| Provider | Starter | 推荐模型 | 适用场景 |
|----------|---------|---------|---------|
| Anthropic Claude | `spring-ai-anthropic-spring-boot-starter` | `claude-sonnet-4-5` | 国际部署，推理质量高 |
| ZhipuAI（智谱 GLM） | `spring-ai-zhipuai-spring-boot-starter` | `glm-4-flash` | 国内部署，成本低 |
| OpenAI / 兼容接口 | `spring-ai-openai-spring-boot-starter` | `gpt-4o-mini` | 或对接任何 OpenAI 兼容 API |
| Ollama（本地模型） | `spring-ai-ollama-spring-boot-starter` | `llama3.2` | 本地开发 / 离线环境 |

**默认配置（开发环境使用 Anthropic，生产按需切换）：**

```xml
<!-- infrastructure/pom.xml — 默认 Anthropic，按需替换 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-anthropic-spring-boot-starter</artifactId>
</dependency>
```

> 切换为 GLM 示例：将上述依赖替换为 `spring-ai-zhipuai-spring-boot-starter`，  
> 再修改 `application.yml` 中的 `spring.ai.*` 配置段，其余代码不动。

### D2: AiService 接口放在 application 层

`AiService` 是领域用例（摘要、评审）的编排依赖，放在 application 层作为接口，implementation 在 infrastructure 层，符合依赖倒置原则。

```
application/.../ai/service/AiService.java          ← 接口
infrastructure/.../ai/AiServiceImpl.java           ← Spring AI 实现
```

`AiService` 方法签名（change#5 需要）：

```java
public interface AiService {
    /** 从 URL 读取文档内容，调用 Claude 摘要出 title 和 content */
    SummarizeResult summarizeFromUrl(String url);

    /** 直接对文本内容摘要（后续 change 复用） */
    SummarizeResult summarizeText(String rawText);
}

public record SummarizeResult(String title, String content) {}
```

### D3: URL 文档读取 — 服务端 WebClient Fetch（非 MCP 工具调用）

两种方案对比：

| | 方案 A：服务端 WebClient fetch | 方案 B：MCP tool 让 Claude 自己 fetch |
|---|---|---|
| 实现复杂度 | 低（WebClient + RestTemplate） | 高（需要 MCP server / function calling） |
| 内部文档认证 | 可注入服务账号 Token | Claude 无法携带内部凭证 |
| 超时控制 | 完全可控 | 依赖 Claude tool 执行 |
| 调试便利性 | 直接看 HTTP 请求 | 需要通过 Claude 日志 |

**选择方案 A**：`WebClient` 服务端拉取文档内容，将原文传给 Claude 做摘要。文档认证凭据（如 Bearer token）通过配置注入，不依赖 Claude 的 tool call 机制。

> 后续如果需要 Claude 使用 MCP 工具（如搜索、代码执行），在 change#17+ 引入，不在本 change 范围内。

```
URL → WebClient.get(url) → rawContent(HTML/Markdown/纯文本)
    → HtmlToTextConverter（Jsoup 提取正文）
    → Claude("请从以下内容摘要出 title 和 content JSON")
    → SummarizeResult{title, content}
```

### D4: HTML 正文提取 — Jsoup

内部文档通常是 HTML（Confluence、Notion 导出、内部 Wiki）。用 Jsoup 提取 `<body>` 正文文本，去除导航栏/页脚噪音，减少 token 消耗。

```xml
<!-- infrastructure pom -->
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
</dependency>
```

如果 Content-Type 是纯文本或 Markdown，跳过 Jsoup 直接传入。

### D5: SSE 工具 — SseEventEmitter

封装 Spring MVC 的 `SseEmitter`，提供类型化的阶段事件推送，供 change#5 PrdController 使用：

```java
public class SseEventEmitter {
    private final SseEmitter emitter;          // 超时 60s

    public void sendFetching(String message);
    public void sendSummarizing(String message);
    public void sendDone(Object data);         // 序列化为 JSON
    public void sendError(String message);
    public void complete();

    public SseEmitter getEmitter();            // 返回给 Controller
}
```

事件 JSON 格式统一：`{"stage":"<stage>","message":"<msg>","data":<data|null>}`

### D6: 超时与错误处理

| 场景 | 策略 |
|------|------|
| WebClient fetch 超时 | 5s connect timeout + 15s read timeout，超时抛 `AiServiceException` |
| Claude API 超时 | `ChatClient` 30s timeout，超时抛 `AiServiceException` |
| Claude API 限流 | 不重试（change#5 通过 SSE error 告知用户重试） |
| 摘要结果解析失败 | 回退策略：title=URL 域名，content=原文前 500 字 |

`AiServiceException` 继承 `RuntimeException`，由 `GlobalExceptionHandler` 捕获返回 `AI_SERVICE_ERROR(99997)`。

### D7: 配置结构（Provider 无关部分固定，Provider 专属部分按需激活）

`ai.fetch.*` 是通用配置，与 provider 无关，始终存在。  
`spring.ai.*` 配置段按实际引入的 starter 填写对应 provider 的 key。

```yaml
# application.yml（通用配置，与 provider 无关）
ai:
  fetch:
    timeout-connect-ms: 5000
    timeout-read-ms: 15000
    user-agent: "PrdReview-Bot/1.0"
```

```yaml
# application-dev.yml（默认 Anthropic，可按需替换整个块）

# ── 使用 Anthropic Claude ──────────────────────────────
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}          # export ANTHROPIC_API_KEY=sk-ant-...
      chat:
        options:
          model: ${AI_MODEL:claude-sonnet-4-5}
          max-tokens: 1024
          temperature: 0.3

# ── 切换为 ZhipuAI GLM（注释上方块，取消注释此块）──────
# spring:
#   ai:
#     zhipuai:
#       api-key: ${ZHIPUAI_API_KEY}          # export ZHIPUAI_API_KEY=xxx.yyy
#       chat:
#         options:
#           model: ${AI_MODEL:glm-4-flash}
#           max-tokens: 1024
#           temperature: 0.3

# ── 切换为本地 Ollama（无需 API Key）─────────────────────
# spring:
#   ai:
#     ollama:
#       base-url: http://localhost:11434
#       chat:
#         options:
#           model: ${AI_MODEL:llama3.2}
```

`AiProperties` 读取 `ai.fetch.*` 配置，注入 `AiServiceImpl`。  
`AiServiceImpl` 注入 Spring AI `ChatClient` Bean（由激活的 starter 自动配置），代码与 provider 完全解耦。

## Risks / Trade-offs

- **Provider 切换的提示词兼容性**：不同模型对 Prompt 的理解有差异，切换 provider 后摘要 Prompt 可能需要微调（尤其是 JSON 输出格式指令）。→ `AiServiceImpl` 内的 Prompt 应尽量简洁、模型无关；如摘要质量下降，先调整 Prompt 而非改代码。
- **内部文档需要认证**：若内部 Wiki 需要 SSO/Cookie，WebClient 无法直接访问。→ 短期风险可接受（dev 环境文档通常可直接访问）；正式上线前通过配置注入 `Authorization` header 或 Cookie。
- **HTML 正文提取误差**：Jsoup 提取质量依赖页面结构，导航栏未清干净时 token 浪费。→ 可在摘要 Prompt 中指示 Claude 忽略无关内容。
- **`SseEmitter` 线程安全**：Spring MVC `SseEmitter.send()` 非线程安全，需在单一线程中调用，或加锁。→ `AiServiceImpl.summarizeFromUrl()` 在异步线程池中顺序执行，不存在并发 send。
- **Spring AI 1.0.0 API 变动**：Spring AI 历史上 API 变动频繁。→ `AiService` 接口层屏蔽 Spring AI 细节，未来升级只改 `AiServiceImpl`。

## Migration Plan

1. parent pom 引入 `spring-ai-bom`
2. infrastructure pom 引入 `spring-ai-anthropic-spring-boot-starter` + `jsoup`
3. `application-dev.yml` 补充 `spring.ai.anthropic.*` 配置（API Key 从 env 读取）
4. 本地开发：`export ANTHROPIC_API_KEY=sk-ant-...`

## Open Questions

（无）
