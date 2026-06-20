## 0. 前置：确认错误码

- [x] 0.1 在 `ErrorCode` 枚举 30000 段位确认/新增以下错误码：`PRD_NOT_FOUND(30001)`、`PRD_VERSION_CONFLICT(30004)`（已有），新增 `PRD_OPERATION_NOT_ALLOWED(30005, "当前状态不允许该操作")`；运行 `ErrorCodeTest` 验证唯一性

## 1. 数据库迁移脚本

- [x] 1.1 创建 `db/migration/V3__create_prd_tables.sql`：建 `prd` 表（id, title VARCHAR(200), content MEDIUMTEXT, source_url VARCHAR(2048), author_id BIGINT, status VARCHAR(20) DEFAULT 'DRAFT', version INT DEFAULT 1, deleted TINYINT DEFAULT 0, created_at DATETIME, updated_at DATETIME）
- [x] 1.2 同文件新增 `prd_version` 表（id, prd_id BIGINT, version INT, title VARCHAR(200), content MEDIUMTEXT, source_url VARCHAR(2048), created_at DATETIME），添加 FK 索引（prd_id）

> `prd_version` 含 `source_url` 字段，与 `PrdVersion` 领域对象保持一致（均含该字段）。

## 2. Domain 层

- [x] 2.1 创建 `domain/.../prd/model/PrdStatus.java` 枚举（INITIALIZING / DRAFT / SUBMITTED / UNDER_REVIEW / APPROVED / REJECTED）
- [x] 2.2 创建 `domain/.../prd/model/Prd.java` 聚合根（纯 Java 对象，无 MyBatis 注解）
  - 字段：id、title、content、sourceUrl、authorId、status、version、createdAt、updatedAt
  - 静态工厂：`createFromManual(title, content, authorId)` → status=DRAFT；`createFromUrl(sourceUrl, authorId)` → status=INITIALIZING
  - 行为方法：`submit()`（DRAFT 才可调用，否则抛 `PRD_OPERATION_NOT_ALLOWED`）、`completeInitialization(title, content)`（INITIALIZING 才可调用）、`startReview()`（桩）、`approve()`（桩）、`reject()`（桩）
  - 查询方法：
    - `isOwnedBy(userId)`
    - `isVisibleTo(Long userId, String role)`：ADMIN/TEAM_MEMBER 可见全部；SUBMITTER 仅可见自己且非 INITIALIZING
    - `isEditable()`：仅 DRAFT 可编辑
    - `isDeletableBy(Long userId, String role)`：DRAFT 或 INITIALIZING 状态，且本人或 ADMIN
- [x] 2.3 创建 `domain/.../prd/model/PrdVersion.java` 实体（id、prdId、version、title、content、**sourceUrl**、createdAt）
- [x] 2.4 创建 `domain/.../prd/repository/PrdRepository.java` 接口（findById、save、update、softDelete、findPageByCondition）
- [x] 2.5 创建 `domain/.../prd/repository/PrdVersionRepository.java` 接口（save、findByPrdId）

## 3. Infrastructure 层

- [x] 3.1 创建 `PrdPO.java`（`@TableName("prd")`、`@Version`、`@TableLogic`，含 sourceUrl、status、deleted 字段）
- [x] 3.2 创建 `PrdVersionPO.java`（`@TableName("prd_version")`，含 **sourceUrl** 字段）
- [x] 3.3 创建 `PrdMapper.java`（继承 `BaseMapper<PrdPO>`）
- [x] 3.4 创建 `PrdVersionMapper.java`（继承 `BaseMapper<PrdVersionPO>`）
- [x] 3.5 创建 `PrdAssembler.java`（PrdPO ↔ Prd、PrdVersionPO ↔ PrdVersion 双向转换，处理 PrdStatus 枚举 ↔ String）
- [x] 3.6 实现 `PrdRepositoryImpl.java`：`findPageByCondition` 根据 authorId/role 组装 `LambdaQueryWrapper`；列表查询排除 INITIALIZING 状态；**固定 ORDER BY created_at DESC**
- [x] 3.7 实现 `PrdVersionRepositoryImpl.java`
- [x] 3.8 在 `bootstrap` 的 `MybatisPlusConfig` 中新建/更新配置 Bean，同时注册 `OptimisticLockerInnerInterceptor`（乐观锁）和 `PaginationInnerInterceptor(DbType.MYSQL)`（分页，`findPageByCondition` 依赖）；当前两者均未注册

## 4. Application 层

- [x] 4.1 创建 Command/Query 对象：`CreatePrdCommand`（title、content）、`CreatePrdFromUrlCommand`（sourceUrl）、`UpdatePrdCommand`（title、content、version）、`PrdQueryCommand`（page、size、currentUserId、currentUserRole）
- [x] 4.2 创建 `PrdDTO`（id、title、content、sourceUrl、authorId、status、version、createdAt、updatedAt）和 `PrdPageResult`（total、items）
- [x] 4.3 实现 `PrdApplicationService.createManual()`：校验 title/content 非空，调用 `Prd.createFromManual()`，Repository 保存，返回 PrdDTO
- [x] 4.4 实现 `PrdApplicationService.createFromUrl()`：调用 `Prd.createFromUrl()`，Repository 保存，返回 prdId（供 SSE 端点异步后续处理）
- [x] 4.5 实现 `PrdApplicationService.completeInitialization(prdId, title, content)`：调用 `prd.completeInitialization()`，Repository 更新，返回 PrdDTO
- [x] 4.6 实现 `PrdApplicationService.getById(prdId, currentUserId, currentUserRole)`：调用 `prd.isVisibleTo(userId, role)` 权限检查（不可见则抛 `PRD_NOT_FOUND`），返回 PrdDTO
- [x] 4.7 实现 `PrdApplicationService.listPrds()`：按角色过滤（传入 currentUserRole），分页查询（排除 INITIALIZING，ORDER BY created_at DESC），返回 PrdPageResult
- [x] 4.8 实现 `PrdApplicationService.updateDraft()`：调用 `prd.isEditable()` + `prd.isOwnedBy()` 校验，执行乐观锁更新，返回 PrdDTO
- [x] 4.9 实现 `PrdApplicationService.softDelete(prdId, currentUserId, currentUserRole)`：调用 `prd.isDeletableBy(userId, role)` 校验（DRAFT 或 INITIALIZING 均可删），执行逻辑删除
- [x] 4.10 实现 `PrdApplicationService.submitPrd()`：`@Transactional`，调用 `prd.submit()` + 创建 PrdVersion 快照（含 sourceUrl 字段）

## 5. API 层

- [x] 5.1 创建请求 DTO（两个独立 DTO，无互斥校验复杂性）：
  - `CreatePrdRequest`：`@NotBlank title`、`@NotBlank content`
  - `CreatePrdFromUrlRequest`：`@NotBlank @URL sourceUrl`
  - `UpdatePrdRequest`：`@NotBlank title`、`@NotBlank content`、`@NotNull version`
- [x] 5.2 创建响应 DTO：`PrdResponse`（id、title、content、sourceUrl、authorId、status、version、createdAt、updatedAt）
- [x] 5.3 实现 `PrdController`：`POST /api/v1/prds`（手动路径）
  - 同步调用 `createManual()`，返回 `Result<PrdResponse>`
- [x] 5.4 实现 `PrdController`：`POST /api/v1/prds/from-url`（URL → AI 摘要，SSE 流式）
  - `produces = MediaType.TEXT_EVENT_STREAM_VALUE`，返回 `SseEmitter`（60s 超时）
  - 调用 `createFromUrl()` 得到 prdId，创建 `SseEventEmitter`
  - **`CompletableFuture.runAsync()`** 异步执行：`aiService.summarizeFromUrl(url)` → `completeInitialization()` → `sseEmitter.sendDone(prdResponse)`；异常时 `sseEmitter.sendError(message)`
- [x] 5.5 实现 `PrdController`：`GET /api/v1/prds/{id}`（getById，传入 currentUserId + currentUserRole）
- [x] 5.6 实现 `PrdController`：`GET /api/v1/prds`（listPrds，含 page/size 参数，默认 page=1 size=20）
- [x] 5.7 实现 `PrdController`：`PUT /api/v1/prds/{id}`（updateDraft）
- [x] 5.8 实现 `PrdController`：`DELETE /api/v1/prds/{id}`（softDelete，传入 currentUserRole）
- [x] 5.9 实现 `PrdController`：`POST /api/v1/prds/{id}/submit`（submitPrd）

## 6. 异常处理补全

- [x] 6.1 在 `GlobalExceptionHandler` 中捕获 `OptimisticLockingFailureException`，返回 `Result.error(ErrorCode.PRD_VERSION_CONFLICT)`

## 7. 测试

- [x] 7.1 `PrdTest`（纯领域单元测试，无 Spring 上下文）：
  - `createFromManual` / `createFromUrl` 工厂方法初始状态正确
  - `submit()` 成功（DRAFT → SUBMITTED）/ INITIALIZING 调用 submit 抛异常
  - `completeInitialization()` 成功（INITIALIZING → DRAFT）/ DRAFT 调用抛异常
  - `isVisibleTo` ADMIN 可见所有 / SUBMITTER 仅见自己且非 INITIALIZING
  - `isDeletableBy` DRAFT 本人可删 / SUBMITTED 不可删 / ADMIN 可删他人 DRAFT
- [x] 7.2 `PrdApplicationServiceTest`：createManual 成功 + title/content 为空时校验失败
- [x] 7.3 `PrdApplicationServiceTest`：createFromUrl → completeInitialization 完整流程（Mock PrdRepository + AiService）
- [x] 7.4 `PrdApplicationServiceTest`：getById 本人/非本人/ADMIN/INITIALIZING 可见性（传入不同 role 参数）
- [x] 7.5 `PrdApplicationServiceTest`：updateDraft 成功 / 版本冲突 / 非 DRAFT 状态 / 非本人
- [x] 7.6 `PrdApplicationServiceTest`：softDelete 成功（DRAFT + INITIALIZING）/ SUBMITTED 不可删 / 非本人（非 ADMIN）
- [x] 7.7 `PrdApplicationServiceTest`：submitPrd 成功（快照含 sourceUrl）/ INITIALIZING 不可提交 / 非本人
- [x] 7.8 `PrdApplicationServiceTest`：listPrds SUBMITTER 只看自己 / ADMIN 看全部 / 均排除 INITIALIZING / 结果按 created_at DESC 排序
