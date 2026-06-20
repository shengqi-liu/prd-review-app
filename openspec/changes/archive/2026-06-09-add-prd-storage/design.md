## Context

系统已具备用户鉴权（JWT + BCrypt）和 RBAC（`@RequireRole` AOP）。PRD 是核心业务实体，需要在 DDD 五模块架构下实现存储层，支撑后续 AI 评审流程。

当前状态：数据库中只有 `user` 表（Flyway V1/V2），无任何 PRD 相关表。

本 change 依赖 **change#4.5 add-ai-infrastructure**（Spring AI + AiService + SSE 基础设施），需先完成 #4.5。

## Goals / Non-Goals

**Goals:**
- 建立 `Prd` 聚合根（富血模型，状态机封装在聚合根内）及完整 CRUD 接口
- 支持两种创建路径：手动填写 和 URL → AI 摘要（SSE 流式）
- 实现 DRAFT→SUBMITTED 状态机 + 版本快照
- 乐观锁防并发冲突，逻辑删除（soft delete）
- 基于角色的数据访问控制（SUBMITTER 只能看自己，ADMIN/TEAM_MEMBER 可看全部）

**Non-Goals:**
- PRD 内容质量校验（change#6）
- 文件上传/PDF 解析（change#7）
- 触发 AI 评审流程（change#17 起）
- APPROVED/REJECTED 状态的转换（change#20）

## Decisions

### D1: 富血领域模型

`Prd` 聚合根封装状态机，ApplicationService 只做编排，不写状态判断逻辑。

```java
// 聚合根内部封装不变量
public void submit() {
    if (this.status != PrdStatus.DRAFT)
        throw new BizException(ErrorCode.PRD_OPERATION_NOT_ALLOWED);
    this.status = PrdStatus.SUBMITTED;
}

public void completeInitialization(String title, String content) {
    if (this.status != PrdStatus.INITIALIZING)
        throw new BizException(ErrorCode.PRD_OPERATION_NOT_ALLOWED);
    this.title = title;
    this.content = content;
    this.status = PrdStatus.DRAFT;
}

// ApplicationService 只做编排
public PrdDTO submitPrd(Long prdId, Long userId) {
    Prd prd = repository.findById(prdId);
    if (!prd.isOwnedBy(userId)) throw new BizException(ErrorCode.FORBIDDEN);
    prd.submit();  // 规则封装在聚合根
    repository.save(prd);
    ...
}
```

### D2: 领域对象与持久化对象分离（PO 分离）

与 `User` 实体（直接使用 MyBatis-Plus 注解）不同，`Prd` 采用 PO 分离策略，保持领域模型纯净：

| 层 | 类名 | 说明 |
|----|------|------|
| domain | `Prd`（聚合根） | 纯 Java 对象，有行为方法，无 MyBatis 注解 |
| domain | `PrdVersion`（实体） | 纯 Java 对象，无 MyBatis 注解 |
| infrastructure | `PrdPO` | `@TableName("prd")`、`@Version`、`@TableLogic`，无行为 |
| infrastructure | `PrdVersionPO` | `@TableName("prd_version")`，无行为 |
| infrastructure | `PrdAssembler` | PrdPO ↔ Prd、PrdVersionPO ↔ PrdVersion 双向转换 |
| application | `PrdDTO` / `PrdPageResult` | Application 层输出 |
| api | `CreatePrdRequest` / `CreatePrdFromUrlRequest` / `UpdatePrdRequest` / `PrdResponse` | HTTP 层 DTO |

`version` 字段双边存在：`PrdPO.version` 由 MyBatis-Plus 管理（写库时 +1），`Prd.version` 读给前端用于乐观锁校验。

### D3: 乐观锁实现 — MyBatis-Plus @Version

MyBatis-Plus 的 `@Version` 注解在 UPDATE 语句自动追加 `WHERE version = ?` 并自增。update 影响行数为 0 时抛出 `OptimisticLockingFailureException`，在 `GlobalExceptionHandler` 统一捕获返回 `PRD_VERSION_CONFLICT(30004)`。

### D4: 逻辑删除 — MyBatis-Plus @TableLogic

`@TableLogic` 字段 `deleted`（tinyint, 默认 0）仅存在于 `PrdPO`，不暴露到领域模型。MyBatis-Plus 自动在所有查询条件追加 `AND deleted = 0`，DELETE 转为 UPDATE。

### D5: 分页 + 排序 — MyBatis-Plus Page + 条件构造器

`PrdRepositoryImpl` 使用 `LambdaQueryWrapper` 按角色过滤（SUBMITTER 加 authorId 条件，列表查询额外排除 INITIALIZING 状态），ADMIN/TEAM_MEMBER 不加 authorId 过滤。`Page<PrdPO>` 传入 `baseMapper.selectPage()`。

**排序**：列表查询固定 `ORDER BY created_at DESC`，保证分页结果稳定。

### D6: 两种创建路径 + INITIALIZING 状态

URL 路径引入 `INITIALIZING` 中间状态，保持聚合根不变量（DRAFT 阶段 title/content 必须有值）。两种路径拆为**两个独立端点**，避免同一端点混用 `Result<PrdResponse>` 和 `SseEmitter` 两种响应类型：

```
手动路径：
  POST /api/v1/prds               { title, content }
       ↓ 同步返回 Result<PrdResponse>，status=DRAFT

URL 路径（SSE）：
  POST /api/v1/prds/from-url      { source_url }
       ↓ 同步：创建 Prd{status=INITIALIZING}，返回 SseEmitter（text/event-stream）
       ↓ 异步（CompletableFuture.runAsync）推送 SSE 事件
       ↓ AI 完成：prd.completeInitialization(title, content) → status=DRAFT
       ↓ SSE done 事件携带完整 PrdResponse
```

INITIALIZING 状态对外不可见（列表接口排除；GET 详情时非本人返回 NOT_FOUND，本人返回当前状态供前端显示"初始化中"）。

### D7: SSE 实现 — Spring MVC SseEmitter + 虚拟线程

`POST /api/v1/prds/from-url` 返回 `SseEmitter`（60s 超时）。AI 处理委托给 **change#4.5** 提供的 `AiService.summarizeFromUrl(url)`，在 `CompletableFuture.runAsync()` 中异步执行（Spring Boot 3 已开启虚拟线程，无需额外线程池配置）。

事件结构（复用 change#4.5 的 `SseEventEmitter`）：

```json
{"stage":"fetching",    "message":"正在读取文档..."}
{"stage":"summarizing", "message":"AI 正在分析内容..."}
{"stage":"done",        "message":"处理完成", "data":{...PrdResponse...}}
{"stage":"error",       "message":"...错误信息..."}
```

### D8: 版本快照事务边界

`PrdApplicationService.submitPrd()` 加 `@Transactional`，在同一事务内完成：
1. 查询并调用 `prd.submit()`（内部校验状态）
2. 更新 Prd（触发乐观锁 version+1）
3. 插入 PrdVersion 快照

### D9: `isVisibleTo` 需携带角色信息

可见性规则依赖 role（ADMIN/TEAM_MEMBER 可见所有 PRD，SUBMITTER 只能看自己的），因此聚合根方法签名为：

```java
public boolean isVisibleTo(Long userId, String role) {
    if ("ADMIN".equals(role) || "TEAM_MEMBER".equals(role)) return true;
    return isOwnedBy(userId) && this.status != PrdStatus.INITIALIZING;
}
```

ApplicationService 从 `CurrentUser` 上下文获取 role 后传入，聚合根不依赖外部服务。

### D10: CreatePrdRequest 互斥校验 — @AssertTrue

`CreatePrdRequest`（手动路径）和 `CreatePrdFromUrlRequest`（URL 路径）拆为两个 DTO，各自独立校验，天然消除互斥校验问题：

```java
// 手动路径
public class CreatePrdRequest {
    @NotBlank String title;
    @NotBlank String content;
}

// URL 路径
public class CreatePrdFromUrlRequest {
    @NotBlank @org.hibernate.validator.constraints.URL String sourceUrl;
}
```

### D11: PRD 域错误码（30000–39999 段位）

确认 `ErrorCode` 枚举中 PRD 相关码：

| 错误码 | 值 | 说明 |
|--------|----|------|
| `PRD_NOT_FOUND` | 30001 | PRD 不存在或无权限 |
| `PRD_CONTENT_TOO_SHORT` | 30002 | 内容过短（change#6 使用） |
| `PRD_MISSING_REQUIRED_SECTION` | 30003 | 缺少必要章节（change#6 使用） |
| `PRD_VERSION_CONFLICT` | 30004 | 乐观锁冲突 |
| `PRD_OPERATION_NOT_ALLOWED` | 30005 | 当前状态不允许该操作（新增） |

`PRD_OPERATION_NOT_ALLOWED(30005)` 为本 change 新增，在 `ErrorCode` 枚举中补充。

## Risks / Trade-offs

- **INITIALIZING 状态的孤儿记录**：若 AI 失败且用户未手动删除，数据库中会残留 INITIALIZING 状态的 PRD。→ 可在后续 change 中加定时清理任务（超过 10 分钟的 INITIALIZING 记录标记删除），本 change 暂不实现。
- **SSE 超时**：AI 摘要超过 30 秒时 SSE 连接可能被 Nginx/网关断开。→ `SseEmitter` 设置 60s 超时；change#4.5 的 `AiService` 需保证 30s 内返回（超时抛异常，SSE 推送 error 事件）。
- **乐观锁 + 前端版本号**：前端 PUT 请求必须携带 version，`UpdatePrdRequest.version` 加 `@NotNull` 校验。
- **PO 分离 Assembler 开销**：每次读写多一次对象转换。→ 对当前 QPS 可忽略，换来领域模型清晰度值得。

## Migration Plan

1. 先完成 change#4.5（AiService 基础设施）✅
2. 应用启动时 Flyway 自动执行 `V3__create_prd_tables.sql`
3. 无现有数据，无需数据回填
4. 回滚：删除 `prd_version`、`prd` 两表（需手动执行 DROP）

## Open Questions

（无，评审阶段已全部决策）
