## Context

项目当前已完成认证（JWT）、RBAC AOP、AI 基础设施（Spring AI + SSE）。PRD 存储是后续所有评审链路（#6 输入验证、#17 内审、#18 多 Agent 评审）的先决条件。MyBatis-Plus 已在 infrastructure 模块配置，`MetaObjectHandlerConfig` 自动填充 `createdAt`/`updatedAt`，逻辑删除通过 `@TableLogic` + `deleted` 字段实现。

## Goals / Non-Goals

**Goals:**
- 实现 PRD 聚合根完整状态机（不含 startReview/approve/reject，留给后续 change）
- 提供手动路径（同步）和 URL 路径（SSE 异步）两种创建方式
- 完成 CRUD + 提交评审 + 版本快照 REST API
- 前端 PRD 列表和创建/编辑页（Vanilla JS，与现有 login.html 风格一致）

**Non-Goals:**
- PDF/Word 解析（#7）
- PRD 输入门槛验证（#6）
- AI 评审触发（#17+）
- `startReview()`/`approve()`/`reject()` 等高阶状态转换方法的实现

## Decisions

### D1：状态机行为内聚于聚合根
在 `Prd` 领域对象上暴露 `submit()`、`completeInitialization()` 等方法，抛出 `BizException(ErrorCode.OPERATION_NOT_ALLOWED)` 而非在 Service 散落 if-else。符合 DDD 聚合根封装原则，且 ArchUnit 测试已在守护 domain 层不引入 Spring 注解。

### D2：URL 路径 SSE 实现
复用 `api` 模块已有的 `SseEmitterPool`（add-ai-infrastructure 引入），在 `PrdApplicationService` 中异步执行抓取 + AI 摘要，通过 `CompletableFuture` + 虚拟线程池发射 SSE 事件。抓取工具使用 `RestTemplate`（已在 bootstrap 配置），AI 摘要调用 `AiService.streamChat()`。失败时推送 `error` 事件，PRD 保持 `INITIALIZING` 状态（由用户决定重试或删除）。

### D3：乐观锁冲突处理
MyBatis-Plus `@Version` 注解在更新时自动校验 version 字段；若 update 影响行数为 0，Service 捕获并抛出 `BizException(ErrorCode.PRD_VERSION_CONFLICT)`。

### D4：列表可见性
- `SUBMITTER` 角色：WHERE author_id = ? AND status != 'INITIALIZING' AND deleted = 0
- `ADMIN`/`TEAM_MEMBER`：WHERE status != 'INITIALIZING' AND deleted = 0

在 `PrdMapper` 中用 MyBatis-Plus `QueryWrapper` 动态拼接，避免两条 XML。

### D5：版本快照在 Service 层创建
`submit()` 调用链：`prd.submit()` → `prdRepository.save(prd)` → `prdVersionRepository.save(snapshot)`，两步操作包在同一 `@Transactional` 内，保证原子性。

### D6：前端页面
新增 `prd-list.html` 和 `prd-edit.html`，用 Fetch API 对接后端，复用现有 CSS 风格（Bootstrap 5 CDN）。JWT token 从 `localStorage.getItem('token')` 读取，`Authorization: Bearer <token>` 头注入。

## Risks / Trade-offs

- **SSE 连接泄漏**：AI 摘要超时（如网络问题）会导致 SSE 长连接挂起 → 为 CompletableFuture 设置 30s 超时，超时后推送 error 并 `emitter.complete()`
- **INITIALIZING 状态孤儿**：用户创建后关闭页面，PRD 永久停在 INITIALIZING → 可在后续 change 加定时任务清理，本 change 不处理
- **逻辑删除与 MyBatis-Plus 全局配置**：需确认 `application.yml` 中 `global-config.db-config.logic-delete-field=deleted` 已配置，否则 `@TableLogic` 不生效

## Migration Plan

1. 手动执行 `db/migration/V3__create_prd_tables.sql`（项目未启用 Flyway 自动执行）
2. `mvn install -DskipTests` 重新打包所有模块
3. `mvn -pl bootstrap spring-boot:run` 启动应用
4. 回滚：DROP TABLE prd_version; DROP TABLE prd;（数据可恢复，无外键约束）
