## ADDED Requirements

### Requirement: PRD 聚合根
系统 SHALL 维护 `Prd` 聚合根，包含以下字段：`id`（Long，自增主键）、`title`（标题，AI 摘要或手动填写，可在 DRAFT 阶段为空）、`content`（全文，可在 DRAFT 阶段为空）、`sourceUrl`（原始文档 URL，nullable，URL 路径创建时保留）、`authorId`（Long，关联 User.id，创建后不可变）、`status`（PrdStatus 枚举，默认 INITIALIZING 或 DRAFT）、`version`（Integer，乐观锁，从 1 开始，每次更新 +1）、`createdAt`（创建时间，自动填充）、`updatedAt`（最后修改时间，自动更新）。

聚合根 SHALL 暴露以下领域行为方法，内部封装状态机不变量：
- `submit()`：DRAFT → SUBMITTED，非 DRAFT 时抛 OPERATION_NOT_ALLOWED
- `startReview()`：SUBMITTED → UNDER_REVIEW（change#17 实现）
- `approve()`：UNDER_REVIEW → APPROVED（change#20 实现）
- `reject()`：UNDER_REVIEW → REJECTED（change#20 实现）
- `completeInitialization(title, content)`：INITIALIZING → DRAFT，填充 AI 摘要结果
- `isOwnedBy(userId)`：返回 authorId.equals(userId)
- `isEditable()`：status == DRAFT
- `isDeletableBy(userId, role)`：status == DRAFT && (isOwnedBy(userId) || role == ADMIN)
- `isVisibleTo(userId)`：isOwnedBy(userId) || status != DRAFT && status != INITIALIZING

#### Scenario: 手动路径创建字段完整性
- **WHEN** 已登录用户提交 title + content（无 sourceUrl）成功创建 PRD
- **THEN** `status` MUST 为 `DRAFT`，`version` MUST 为 `1`，`authorId` MUST 等于当前登录用户 id，`sourceUrl` MUST 为 null

#### Scenario: URL 路径创建字段完整性
- **WHEN** 已登录用户提交 sourceUrl 创建 PRD
- **THEN** `status` MUST 为 `INITIALIZING`，`title` 和 `content` MUST 为空，`sourceUrl` MUST 等于提交的 URL

#### Scenario: authorId 不可变
- **WHEN** 更新 PRD 的任意字段
- **THEN** `authorId` MUST NOT 发生变化

---

### Requirement: PrdStatus 状态枚举
系统 SHALL 定义 `PrdStatus` 枚举，值为：`INITIALIZING`（URL 路径初始化中）、`DRAFT`（草稿）、`SUBMITTED`（已提交评审）、`UNDER_REVIEW`（评审中）、`APPROVED`（通过）、`REJECTED`（不通过）。数据库存储为 VARCHAR。

APPROVED 和 REJECTED 为终态，无任何出口转换。REJECTED 后如需修改，MUST 新建一份 PRD。

#### Scenario: 状态值覆盖
- **WHEN** 查询 PrdStatus.values()
- **THEN** MUST 包含且仅包含 INITIALIZING / DRAFT / SUBMITTED / UNDER_REVIEW / APPROVED / REJECTED 六个值

#### Scenario: 终态不可转换
- **WHEN** 对 APPROVED 或 REJECTED 状态的 PRD 调用 submit()
- **THEN** 系统 MUST 抛出 OPERATION_NOT_ALLOWED

---

### Requirement: PRD 版本快照
系统 SHALL 维护 `PrdVersion` 实体，在每次 PRD 状态由 DRAFT 变为 SUBMITTED 时自动拍取快照，字段：`id`（Long）、`prdId`（Long）、`version`（Integer，对应提交时 Prd.version）、`title`（快照标题）、`content`（快照全文）、`sourceUrl`（快照时的 sourceUrl，nullable）、`createdAt`（快照时间）。

#### Scenario: 提交时创建快照
- **WHEN** 调用 POST /api/v1/prds/{id}/submit 成功
- **THEN** `prd_version` 表 MUST 新增一条记录，`version` 等于提交时 Prd.version，`title`、`content`、`sourceUrl` 为提交瞬间的值

---

### Requirement: URL 路径创建 PRD（SSE 流式）
系统 SHALL 提供基于 SSE（Server-Sent Events）的 PRD 创建接口，当请求体仅包含 `sourceUrl` 时触发 URL 路径：
1. 立即在数据库创建 status=INITIALIZING 的 PRD 并返回 `id`
2. 通过 SSE 推送阶段事件：`fetching`（正在读取文档）→ `summarizing`（AI 正在分析）→ `done`（完成，携带完整 PrdResponse）
3. AI 完成后调用 `completeInitialization(title, content)` 将 status 变为 DRAFT
4. 若 AI 失败，SSE 推送 `error` 事件，PRD 保持 INITIALIZING 状态（可重试或删除）

SSE 事件格式：
```
data: {"stage":"fetching","message":"正在读取文档..."}
data: {"stage":"summarizing","message":"AI 正在分析内容..."}
data: {"stage":"done","prd":{...PrdResponse...}}
data: {"stage":"error","message":"读取失败，请检查 URL 或手动填写"}
```

#### Scenario: SSE 阶段事件顺序
- **WHEN** 提交合法的 sourceUrl
- **THEN** SSE MUST 依次推送 fetching → summarizing → done 三个阶段事件，done 事件携带完整 PrdResponse，status 为 DRAFT

#### Scenario: AI 摘要后用户手动修改
- **WHEN** SSE done 事件返回后，用户对 title 或 content 不满意，调用 PUT /api/v1/prds/{id}
- **THEN** 更新 MUST 成功（status 为 DRAFT，允许编辑），sourceUrl 保持不变

#### Scenario: URL 不可访问时的 SSE 错误
- **WHEN** sourceUrl 指向的文档无法读取
- **THEN** SSE MUST 推送 error 事件，PRD status 保持 INITIALIZING，不转为 DRAFT

---

### Requirement: 手动路径创建 PRD
系统 SHALL 支持通过手动提供 `title` 和 `content` 创建 PRD 草稿，接口为 `POST /api/v1/prds`，需登录，直接返回 PrdResponse（同步，无 SSE）。

#### Scenario: 手动创建成功
- **WHEN** 已登录用户提交合法的 title + content（无 sourceUrl）
- **THEN** 系统 MUST 同步返回 `{"code":0,"data":{"id":...,"status":"DRAFT","version":1,...}}`

#### Scenario: 未登录创建
- **WHEN** 未携带 JWT 调用创建接口
- **THEN** 系统 MUST 返回错误码 `20001`（未登录），HTTP 200

#### Scenario: 参数校验 — 手动路径 title 为空
- **WHEN** 手动路径下 title 为空字符串或缺失（且无 sourceUrl）
- **THEN** 系统 MUST 返回错误码 `10002`（参数不合法）

---

### Requirement: 获取 PRD 详情
系统 SHALL 提供 `GET /api/v1/prds/{id}` 接口，需登录。

#### Scenario: 本人查看任意状态
- **WHEN** 当前用户是该 PRD 的 authorId
- **THEN** 无论 status 为何值（含 INITIALIZING），均 MUST 返回完整 PrdResponse

#### Scenario: 非本人查看 DRAFT 或 INITIALIZING
- **WHEN** 当前用户不是 authorId，且 PRD status 为 DRAFT 或 INITIALIZING
- **THEN** 系统 MUST 返回错误码 `30001`（PRD 方案不存在），HTTP 200（不暴露存在性）

#### Scenario: 非本人查看 SUBMITTED 及以上
- **WHEN** 当前用户不是 authorId，PRD status 为 SUBMITTED / UNDER_REVIEW / APPROVED / REJECTED
- **THEN** 系统 MUST 返回 PrdResponse

#### Scenario: PRD 不存在
- **WHEN** 请求不存在的 id
- **THEN** 系统 MUST 返回错误码 `30001`（PRD 方案不存在）

---

### Requirement: 分页查询 PRD 列表
系统 SHALL 提供 `GET /api/v1/prds` 接口，需登录，支持分页参数 `page`（从 1 开始，默认 1）和 `size`（默认 20，最大 100）。列表结果 MUST NOT 包含 INITIALIZING 状态的 PRD（对任何角色均不可见）。

#### Scenario: 普通用户只能查看自己的
- **WHEN** 当前用户角色为 SUBMITTER，调用列表接口
- **THEN** 返回结果 MUST 只包含 authorId 等于当前用户 id 的 PRD，且排除 INITIALIZING 状态

#### Scenario: ADMIN/TEAM_MEMBER 可查看全部
- **WHEN** 当前用户角色为 ADMIN 或 TEAM_MEMBER，调用列表接口
- **THEN** 返回结果 MUST 包含所有用户的 PRD（排除 INITIALIZING 状态）

#### Scenario: 分页参数生效
- **WHEN** 请求 page=2&size=5
- **THEN** 返回第 2 页数据，最多 5 条

---

### Requirement: 更新 PRD 草稿
系统 SHALL 提供 `PUT /api/v1/prds/{id}` 接口，需登录，请求体包含 `title`、`content`、`version`（乐观锁）。仅允许本人修改 DRAFT 状态的 PRD。此接口也用于 AI 摘要后用户手动修正内容。

#### Scenario: 正常更新
- **WHEN** 本人提交正确的 version，PRD 为 DRAFT 状态
- **THEN** 更新成功，返回新 PrdResponse，`version` MUST 等于原 version + 1，`sourceUrl` MUST 保持不变

#### Scenario: 版本冲突
- **WHEN** 提交的 version 与数据库中不一致（并发修改场景）
- **THEN** 系统 MUST 返回错误码 `30004`（PRD 版本冲突）

#### Scenario: 非 DRAFT 状态不允许更新
- **WHEN** 尝试修改 status 不为 DRAFT 的 PRD
- **THEN** 系统 MUST 返回错误码 `10004`（操作不允许）

#### Scenario: 非本人操作
- **WHEN** 非 authorId 用户尝试更新
- **THEN** 系统 MUST 返回错误码 `20002`（无权限）

---

### Requirement: 软删除 PRD
系统 SHALL 提供 `DELETE /api/v1/prds/{id}` 接口，需登录，允许 DRAFT 或 INITIALIZING 状态的 PRD 被本人或 ADMIN 删除（逻辑删除）。

#### Scenario: 本人删除 DRAFT
- **WHEN** authorId 用户删除 DRAFT 状态的 PRD
- **THEN** 操作成功，PRD 从列表中消失，返回 `{"code":0,"data":null}`

#### Scenario: 本人删除 INITIALIZING（取消 URL 读取）
- **WHEN** authorId 用户删除 INITIALIZING 状态的 PRD（AI 还在处理中）
- **THEN** 操作成功，PRD 逻辑删除，后续 SSE 事件可安全忽略

#### Scenario: ADMIN 删除任意用户的 DRAFT 或 INITIALIZING
- **WHEN** ADMIN 删除其他用户的 DRAFT 或 INITIALIZING PRD
- **THEN** 操作成功

#### Scenario: 非 DRAFT/INITIALIZING 状态不允许删除
- **WHEN** 尝试删除 SUBMITTED 及以上状态的 PRD
- **THEN** 系统 MUST 返回错误码 `10004`（操作不允许）

---

### Requirement: 提交 PRD 评审
系统 SHALL 提供 `POST /api/v1/prds/{id}/submit` 接口，需登录，仅 PRD 本人可提交，将 PRD 状态从 DRAFT 变为 SUBMITTED，同时创建版本快照。

#### Scenario: 提交成功
- **WHEN** 本人对 DRAFT 状态的 PRD 调用 submit
- **THEN** PRD status MUST 变为 SUBMITTED，`prd_version` 表 MUST 新增一条快照记录，返回更新后的 PrdResponse

#### Scenario: INITIALIZING 状态不允许提交
- **WHEN** 对 INITIALIZING 状态的 PRD 调用 submit（AI 尚未完成）
- **THEN** 系统 MUST 返回错误码 `10004`（操作不允许）

#### Scenario: 非 DRAFT 状态提交
- **WHEN** 对 SUBMITTED 或更高状态的 PRD 调用 submit
- **THEN** 系统 MUST 返回错误码 `10004`（操作不允许）

#### Scenario: 非本人提交
- **WHEN** 非 authorId 用户调用 submit
- **THEN** 系统 MUST 返回错误码 `20002`（无权限）

---

### Requirement: PRD 数据库迁移脚本
系统 SHALL 提供 Flyway 迁移脚本 `V3__create_prd_tables.sql`，创建 `prd` 表和 `prd_version` 表。

#### Scenario: 迁移脚本幂等执行
- **WHEN** Flyway 执行 V3 脚本
- **THEN** `prd` 表 MUST 存在，包含字段：`source_url`（VARCHAR 2048，nullable）、`status`（含 INITIALIZING 值）、`version`（乐观锁）、`deleted`（逻辑删除）；`prd_version` 表 MUST 包含 `source_url` 字段
