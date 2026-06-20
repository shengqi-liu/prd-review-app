## Why

#8 落地了评审员的 CRUD 与 Prompt 模板校验，但 ADMIN 保存模板后无法立即验证效果——必须等到 #15/#17 整套评审流水线接通才能知道这个 Prompt 写得好不好。这个等待周期太长，ADMIN 调优 Prompt 的反馈循环被切断。

前端原型 `app.html` 在评审员卡片上已经留了"🧪 测试"按钮，当前是 disabled。本 change 把按钮点亮：ADMIN 提供一份示例 PRD（标题 + 内容），系统用评审员当前的 Prompt 模板渲染后调用 Claude，把流式输出实时回吐到前端 Modal——形成"编辑 → 立即试跑 → 看效果 → 再调"的闭环。

## What Changes

- Domain `Reviewer` 聚合根新增 `renderTemplate(prdTitle, prdContent)` 方法：把 `{{prd_title}}` / `{{prd_content}}` 替换为实际值，不做 AI 调用（纯函数，便于测试）
- Domain `AiService` 接口新增 `Flux<String> streamCompletion(String prompt)` 方法（基于 Spring AI ChatClient 的 `.stream()`），逐 token 返回 AI 输出
- 新增 REST 端点 `POST /api/v1/reviewers/{id}/test`：
  - Body：`{ "prdTitle": "...", "prdContent": "..." }`
  - 返回 `text/event-stream` SSE 流
  - 流程：渲染 Prompt → 调用 `streamCompletion` → 把每个 token chunk 通过 SSE `token` 事件实时推送 → 结束时推 `done` → 异常时推 `error`
- `SseEventEmitter` 新增 `sendToken(String chunk)` 方法和 `token` 阶段
- 接口权限：仅 ADMIN 可调用（评审员配置本身就是 ADMIN 专属能力）
- 前端 `reviewer.js` 实现"🧪 测试"按钮的点击处理：
  - 弹出新 Modal「试跑评审员」，含示例 PRD 输入框（含默认示例文案）+ 流式输出展示区
  - 点击「开始试跑」时通过 EventSource 连接 SSE 端点，逐 token 拼接渲染到输出区，支持中途取消
- 前端 `app.html` 增加试跑 Modal 的 HTML/CSS，并解除"🧪 测试"按钮的 disabled 状态

## Capabilities

### New Capabilities

- `reviewer-testing`: 评审员 Prompt 试跑能力的接口、SSE 流式协议、前端联调

### Modified Capabilities

- `reviewer-management`: 在 reviewer-management spec 中追加"试跑按钮可用"的微调（解除 disabled、新增 onclick 跳转）；不改原有 CRUD 任何行为

## Impact

- **新依赖**：无（Spring AI ChatClient 已在 #4.5 引入，Flux 来自已有的 spring-boot-starter-webflux）
- **数据库**：无变更
- **错误码**：复用现有的 `AI_SERVICE_ERROR(99997)`、`REVIEWER_NOT_FOUND(60001)`、`PARAM_INVALID(10002)`、`FORBIDDEN(20002)`
- **新接口**：`POST /api/v1/reviewers/{id}/test`（SSE）
- **领域行为**：`Reviewer.renderTemplate()` 是纯函数，无副作用，可被后续 #15 Prompt Composer 复用
- **AI 调用成本**：试跑会真实调用 Claude，需要 ADMIN 留意；本 change 不引入额外的限流或配额逻辑（依赖 #4.5 已有的超时机制）
- **前端**：原型上的"🧪 测试"按钮从 disabled 变为 active；新增试跑 Modal
