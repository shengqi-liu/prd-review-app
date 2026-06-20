## Context

#4.5 已经搭好 AI 基础设施（`AiService` + `SseEventEmitter` + Spring AI Anthropic starter），#8 完成评审员的 CRUD 与 Prompt 模板校验。本 change 是把这两者拼起来，给 ADMIN 一个调优 Prompt 的快速反馈通道。

设计上有几个明显的选择题需要先回答：

## Goals / Non-Goals

**Goals:**

- ADMIN 能够在评审员管理页直接试跑当前 Prompt 模板，看到真实的 AI 输出
- 输出必须流式（token-by-token），不能等几十秒一次性返回——否则失去"快速调优"的价值
- 评审员模板的渲染逻辑（占位符替换）下沉到 Domain 层，可被 #15 Prompt Composer 复用
- 试跑接口本身无状态、无持久化——纯调用，纯返回

**Non-Goals:**

- 不引入"试跑历史记录"——试跑是临时调试行为，输出不归档
- 不引入"示例 PRD 库"——ADMIN 自己输入或粘贴；前端给默认示例文案足矣
- 不引入 review_style / kb_context 注入——本 change 只测评审员自己的模板；这两者的拼装是 #15 的事
- 不引入 token 计数 / 成本统计——交给运维侧 LLM 平台监控
- 不引入限流——首版相信 ADMIN 不会乱按；后续若有需要再单独 change

## Decisions

### D1: 流式协议 — SSE token 事件，而非 WebSocket / 整段返回

**选择**：复用 `SseEventEmitter` 增加 `token` 阶段事件，每个 token chunk 一次推送，结束时推 `done`。

**理由**：
- 项目已有 SSE 链路（#4.5 引入），无需引入 WebSocket 这种重型依赖
- Spring AI ChatClient 原生支持 `.stream()` 返回 `Flux<ChatResponse>`，与 SSE 单向流是天然契合
- 浏览器原生 EventSource API 实现简单，比 WebSocket 客户端少一半样板代码
- token 一个事件 = 每个 chunk 一次推送，前端用 `evt.data` 拼接即可

**备选**：
- 整段同步返回——速度太慢，ADMIN 等 30s+ 不出文字会怀疑系统死了
- WebSocket——双向通信能力对"试跑"场景过剩；客户端复杂度上去

### D2: 试跑 PRD 数据来源 — Request Body 内联，含默认示例

**选择**：Request body 直接传 `{ prdTitle, prdContent }`，前端 Modal 提供默认示例文案（"会员付费订阅功能"等）便于 ADMIN 一键试跑。

**理由**：
- 试跑场景是"调 Prompt"，PRD 是道具不是主角，越简单越好
- 内联避免了"还要先建一份 PRD 才能试"的额外步骤
- 默认示例让"首次进入 → 看见效果" < 5 秒，调优反馈循环极短

**备选**：
- 从已有 PRD 列表选——多一次接口调用，多一个交互步骤，对调优体验有损
- 上传文件——纯 ADMIN 调试场景，杀鸡用牛刀

### D3: 模板渲染放 Domain 层 — `Reviewer.renderTemplate(title, content)`

**选择**：在 `Reviewer` 聚合根新增 `renderTemplate(String prdTitle, String prdContent)` 方法，返回字符串。

**理由**：
- 渲染规则（哪些占位符、怎么替换）本就是评审员领域知识的延伸，跟 `validatePromptTemplate()` 同源
- 纯函数无副作用，单测无需 Spring 上下文
- 给 #15 Prompt Composer 留好接口——后续 Composer 在外层拼装 review_style/kb_context 后，内层就调用这个方法
- 避免在 ApplicationService 里"散装"做字符串 replace，关注点集中

### D4: ApplicationService 拼接顺序 — 渲染同步 + AI 流式异步

**选择**：
- `ReviewerApplicationService.test(reviewerId, prdTitle, prdContent)` 同步完成「查评审员 + 渲染 Prompt」，返回渲染后的字符串
- Controller 拿到字符串后开线程 `CompletableFuture.runAsync` 调 `aiService.streamCompletion(prompt)`，订阅 `Flux<String>` 推送到 SSE

**理由**：
- 同步部分（查库 + 字符串替换）快，没必要异步
- AI 流式调用必须异步（否则阻塞 servlet 线程池）
- ApplicationService 不持有 SSE，保持"应用服务不感知传输层"的分层洁癖

### D5: 权限 — 仅 ADMIN

**选择**：`@RequireRole(UserRole.ADMIN)`，与评审员 CRUD 一致。

**理由**：
- 试跑会真实消耗 LLM 配额（钱），不能让普通用户触发
- 试跑能"窥探"评审员的内部 Prompt，本就是 ADMIN 专属信息
- 与 #8 的写操作权限对齐，简单一致

### D6: 取消机制 — 客户端断开 SSE 连接即可

**选择**：不实现服务端主动取消接口；浏览器关闭 EventSource 时 SSE 连接断开，Spring MVC 会清理 emitter，`Flux` 订阅自动取消。

**理由**：
- 浏览器原生 `eventSource.close()` 即可触发——客户端简单
- 服务端不需要额外 API；`SseEmitter.onCompletion()` 自动触发资源回收
- AI 调用费用：取消后 token 流停止，但已发出的请求可能已经在 Anthropic 侧扣费——这是 AI 流式 API 的通用特性，无法规避

## Risks / Trade-offs

- **[ADMIN 试跑可能产生大量 AI 调用]** → 没有限流。Mitigation：在 Modal 上加视觉提示"每次试跑会调用真实 AI 接口产生费用"；如果运行中发现滥用，下个 change 加 rate limiter（Bucket4j）。
- **[长 Prompt + 长输出可能超过 SSE 60s 超时]** → `SseEventEmitter` 当前固定 60s 超时。Mitigation：本 change 让 `SseEventEmitter` 构造函数支持自定义 timeout，试跑场景传 180s（3 分钟够长输出）。
- **[Spring AI ChatClient.stream() 异常处理]** → 流中断时需要正确推 `error` 事件给前端而不是静默挂起。Mitigation：用 `.doOnError(...)` + `sseEmitter.sendError()`，并在测试里 mock 一个失败的 Flux 验证。
- **[前端 EventSource 不支持 POST]** → 浏览器原生 EventSource 只支持 GET。Mitigation：用 `fetch()` + `ReadableStream` 读 SSE 响应；或继续用 POST（Spring MVC SSE 端点支持 POST），前端用 fetch+stream 读。本 change 走 fetch+stream 方案。

## Migration Plan

无数据迁移。部署即生效。回滚：删 Controller 方法 + 还原前端按钮 disabled 即可。
