# Reviewer Testing

## Purpose

为 ADMIN 提供"编辑评审员 Prompt → 立即试跑 → 看效果 → 再调"的快速反馈闭环：用一份临时 PRD（标题 + 内容）渲染评审员的 Prompt 模板，通过 SSE 流式调用 LLM（具体 provider 由 ai-infrastructure 决定），逐 token 把 AI 输出回吐到前端。

本 capability 与 reviewer-management 解耦：评审员管理负责 CRUD，本 capability 负责一次性试跑流程；试跑不归档、不持久化。

## Requirements

### Requirement: Reviewer 模板渲染领域行为
`Reviewer` 聚合根 SHALL 暴露 `renderTemplate(String prdTitle, String prdContent)` 方法，把 `promptTemplate` 中的 `{{prd_title}}` 替换为 `prdTitle`、`{{prd_content}}` 替换为 `prdContent`，返回渲染后的字符串。

渲染 SHALL 为纯函数（无副作用，不抛业务异常）。`null` 入参视为空字符串。同一占位符多次出现 SHALL 全部替换。模板中不含占位符时，SHALL 原样返回。

#### Scenario: 标准渲染
- **WHEN** 调用 `renderTemplate("会员订阅", "支持月付年付...")`，模板为 `请评审 {{prd_title}}\n{{prd_content}}`
- **THEN** 返回 `请评审 会员订阅\n支持月付年付...`

#### Scenario: 同一占位符多次出现
- **WHEN** 模板为 `{{prd_title}}…再次：{{prd_title}}`
- **THEN** 两处占位符 MUST 都被替换

#### Scenario: null 参数视为空串
- **WHEN** 调用 `renderTemplate(null, "x")`
- **THEN** `{{prd_title}}` MUST 被替换为空串，方法不抛异常

#### Scenario: 纯文本模板原样返回
- **WHEN** 模板不含任何 `{{}}` 占位符
- **THEN** 返回的字符串 MUST 与原模板完全一致

---

### Requirement: AiService 流式补全接口
`AiService` 接口 SHALL 暴露 `Flux<String> streamCompletion(String prompt)` 方法，调用底层 LLM 的流式 API，逐个返回 token chunk。

实现 SHALL 使用 Spring AI `ChatClient.prompt(prompt).stream().content()`，并在底层异常时通过 Flux 的错误信号传播（不在 Flux 内部 swallow）。

#### Scenario: 流式逐 token 返回
- **WHEN** 调用 `streamCompletion("写一段评审")`，LLM 流式返回 `"评" "审" "结" "果"` 4 个 chunk
- **THEN** Flux MUST 依次发出这 4 个字符串元素

#### Scenario: LLM 异常通过 Flux 错误信号传播
- **WHEN** LLM 调用超时或网络错误
- **THEN** Flux MUST 通过 `onError` 信号传递异常（包装为 `AiServiceException`）

---

### Requirement: 试跑评审员 API
系统 SHALL 提供 `POST /api/v1/reviewers/{id}/test` 接口，让 ADMIN 用一份临时 PRD 试跑指定评审员的 Prompt，并通过 SSE 流式返回 AI 输出。

接口 SHALL：
- 接受 JSON body `{ "prdTitle": string, "prdContent": string }`，两字段均必填非空
- `Content-Type` 响应头为 `text/event-stream`
- 仅 ADMIN 角色可访问，其他角色返回 `FORBIDDEN`
- 评审员不存在或已逻辑删除时返回 `REVIEWER_NOT_FOUND`
- 异步执行 AI 调用，不阻塞 servlet 线程池

SSE 事件序列 SHALL 为：
- 0..N 个 `token` 阶段事件，`data.message` 为 token chunk 文本
- 最后 1 个 `done` 阶段事件（无 data）
- 如发生异常，推送 1 个 `error` 阶段事件后关闭连接

#### Scenario: ADMIN 成功试跑
- **WHEN** ADMIN 发送 POST 含合法 prdTitle 和 prdContent，评审员存在
- **THEN** 响应 MUST 是 SSE 流，包含至少一个 `token` 事件并以 `done` 事件结尾

#### Scenario: 非 ADMIN 被拒绝
- **WHEN** SUBMITTER 或 TEAM_MEMBER 调用此接口
- **THEN** 系统 MUST 返回 `FORBIDDEN`

#### Scenario: 评审员不存在
- **WHEN** ADMIN 发送 POST 但 path 中的 id 在数据库不存在
- **THEN** 系统 MUST 返回 `REVIEWER_NOT_FOUND`，不发起 AI 调用

#### Scenario: prdTitle / prdContent 为空
- **WHEN** ADMIN 发送 POST，prdTitle 或 prdContent 为空字符串
- **THEN** 系统 MUST 返回 `PARAM_INVALID`，不发起 AI 调用

#### Scenario: AI 调用失败
- **WHEN** LLM API 调用超时或返回错误
- **THEN** SSE 流 MUST 推送 `error` 阶段事件后关闭连接，不抛 HTTP 500

#### Scenario: 客户端断开 SSE 连接
- **WHEN** 浏览器关闭 EventSource 或导航离开页面
- **THEN** 服务端 MUST 检测到连接关闭并取消未完成的 Flux 订阅，释放资源

---

### Requirement: SseEventEmitter token 阶段
`SseEventEmitter` SHALL 新增 `sendToken(String chunk)` 方法，推送 `stage=token` 的 SSE 事件，`message` 字段为 chunk 文本。

`SseEventEmitter` SHALL 提供允许自定义 timeout 的构造函数，覆盖默认的 60 秒，以支持长输出场景（如试跑接口默认 180 秒）。

#### Scenario: 推送 token chunk
- **WHEN** 调用 `sendToken("评审")`
- **THEN** SSE 客户端 MUST 收到 `data: {"stage":"token","message":"评审"}` 事件

#### Scenario: 自定义超时
- **WHEN** 通过 `new SseEventEmitter(180_000L)` 构造
- **THEN** 内部 `SseEmitter` 的 timeout MUST 为 180 秒

---

### Requirement: 前端评审员试跑 Modal
前端 `app.html` 的"AI 评审员"页面 SHALL 实现"🧪 测试"按钮的点击处理，该按钮不再为 disabled 状态。

点击后 SHALL 弹出"试跑评审员" Modal，包含：
- 评审员名称展示（只读）
- `prdTitle` 输入框（必填）
- `prdContent` 文本域（必填）
- 「开始试跑」按钮：通过 fetch + ReadableStream 调用 SSE 接口，按 token 拼接渲染到输出区
- 「取消」按钮：中途可调用 `AbortController.abort()` 中断请求并关闭 Modal
- 输出展示区：流式追加 token chunk，支持垂直滚动
- 默认示例文案：首次打开时 prdTitle 与 prdContent 预填一份示例 PRD（例如「会员付费订阅功能」）以便一键试跑

Modal SHALL 在显示费用提示："每次试跑会真实调用 AI 接口并产生费用"。

#### Scenario: ADMIN 试跑评审员
- **WHEN** ADMIN 点击评审员卡片的"🧪 测试"按钮
- **THEN** 页面 MUST 弹出试跑 Modal，prdTitle 与 prdContent 已预填默认示例

#### Scenario: 流式输出渲染
- **WHEN** ADMIN 在 Modal 中点击「开始试跑」，后端 SSE 推送多个 `token` 事件
- **THEN** 输出区 MUST 逐 token 追加内容，自动滚动到最新

#### Scenario: 中途取消
- **WHEN** ADMIN 在流式输出中点击「取消」
- **THEN** 前端 MUST 调用 `AbortController.abort()` 中断 fetch，并关闭 Modal

#### Scenario: 错误事件处理
- **WHEN** 后端推送 `error` 阶段事件
- **THEN** 输出区 MUST 显示错误提示，「开始试跑」按钮重新可点
