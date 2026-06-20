## 1. Domain 层

- [x] 1.1 在 `Reviewer.java` 新增 `renderTemplate(String prdTitle, String prdContent)` 方法：用 `String.replace("{{prd_title}}", title).replace("{{prd_content}}", content)`，null 入参替换为空串
- [x] 1.2 在 `AiService` 接口新增 `reactor.core.publisher.Flux<String> streamCompletion(String prompt)` 方法

## 2. Infrastructure 层

- [x] 2.1 在 `AiServiceImpl` 实现 `streamCompletion`：注入 `ChatClient`，调用 `chatClient.prompt().user(prompt).stream().content()` 返回 `Flux<String>`；用 `.onErrorMap(ex -> new AiServiceException("AI 流式调用失败", ex))` 包装异常

## 3. SSE 工具增强

- [x] 3.1 在 `SseEventEmitter` 新增重载构造函数 `public SseEventEmitter(long timeoutMs)`，原无参构造改为 `this(60_000L)`
- [x] 3.2 在 `SseEventEmitter` 新增 `public void sendToken(String chunk)` 方法，推送 `stage=token` 事件，data 字段省略
- [x] 3.3 新增 `SseEventEmitterTest` 用例：sendToken 推送正确的 JSON；自定义 timeout 构造函数生效

## 4. Application 层

- [x] 4.1 创建 `application/.../reviewer/TestReviewerCommand.java` record（reviewerId、prdTitle、prdContent）
- [x] 4.2 在 `ReviewerApplicationService` 新增 `String renderTestPrompt(TestReviewerCommand cmd)` 方法：校验 prdTitle/prdContent 非空（PARAM_INVALID）、查 Reviewer（不存在抛 REVIEWER_NOT_FOUND）、调用 `reviewer.renderTemplate(...)` 返回渲染后字符串

## 5. API 层

- [x] 5.1 创建 `api/.../reviewer/dto/TestReviewerRequest.java` record（@NotBlank prdTitle、@NotBlank prdContent）
- [x] 5.2 在 `ReviewerController` 新增 `POST /api/v1/reviewers/{id}/test` 端点：
  - `@RequireRole(UserRole.ADMIN)`
  - `produces = MediaType.TEXT_EVENT_STREAM_VALUE`
  - 返回 `SseEmitter`（180s 超时）
  - 同步阶段：调用 `reviewerService.renderTestPrompt(...)` 拿到 prompt 字符串
  - 异步阶段：`CompletableFuture.runAsync` 内订阅 `aiService.streamCompletion(prompt)`：
    - `doOnNext(token -> sseEmitter.sendToken(token))`
    - `doOnComplete(() -> sseEmitter.sendDone(null))`
    - `doOnError(ex -> sseEmitter.sendError(ex.getMessage()))`
    - 调用 `.subscribe()` 启动订阅
  - `sseEmitter.getEmitter().onTimeout` / `onError` / `onCompletion` 中调用 `subscription.dispose()` 取消 Flux

## 6. 测试

- [x] 6.1 `ReviewerTest`：renderTemplate 标准渲染、同一占位符多次出现、null 入参为空串、纯文本模板原样返回（4 个 case）
- [x] 6.2 `ReviewerApplicationServiceTest`：`renderTestPrompt` 成功 + 评审员不存在抛 REVIEWER_NOT_FOUND + prdTitle 空抛 PARAM_INVALID
- [x] 6.3 `AiServiceImplTest`：`streamCompletion` 正常返回 Flux（Mock ChatClient 的 stream() 链路）；底层异常被包装为 AiServiceException

## 7. 前端联调

- [x] 7.1 修改 `frontend/js/reviewer.js`：去掉测试按钮的 `disabled` 属性，绑定 `onclick="openTestModal(${r.id})"`；该函数找到对应 reviewer，填充 Modal 默认示例（标题"会员付费订阅功能"、内容一段示例 PRD）
- [x] 7.2 在 `reviewer.js` 新增 `openTestModal()` / `closeTestModal()` / `startTest()` / `cancelTest()` 函数：
  - `startTest()` 用 `fetch()` POST + `body.getReader()` 读取 SSE 流，解析 `data: {...}` 行，按 stage 分发：`token` 追加到输出区、`done` 显示完成、`error` 显示错误
  - 用 `AbortController` 支持取消
- [x] 7.3 在 `frontend/app.html` 新增「试跑评审员」Modal HTML：评审员名（只读）+ prdTitle 输入 + prdContent textarea + 输出区（`<pre>` + 自动滚动） + 「开始试跑/取消/关闭」按钮 + 费用提示
- [x] 7.4 在 `frontend/app.html` 增加试跑 Modal 的 CSS（复用现有 `.modal-mask`/`.modal-box`/`.form-row` 样式，加 `.test-output { font-family: monospace; min-height: 200px; max-height: 400px; overflow-y: auto; ...}`)
- [x] 7.5 在 `reviewer.js` 末尾 `window.openTestModal = openTestModal; ...` 暴露给 HTML inline handler

## 8. 路线图与文档

- [x] 8.1 实现完成后，由 `/opsx:archive` 自动更新 `openspec/roadmap.md` 中 #9 状态为 ✅ DONE
