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

在状态转移之前 SHALL 先执行输入门槛校验：title 与 content 必须满足最低字数；content 必须包含至少 2 个核心章节（背景 / 目标 / 功能设计，详见 "PRD 提交输入门槛" requirement）。校验失败时 SHALL 直接抛对应 BizException，不进入状态机，不创建快照。

#### Scenario: 提交成功
- **WHEN** 本人对 DRAFT 状态的 PRD 调用 submit，且 title/content 满足门槛、含必要章节
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

#### Scenario: title 太短被拒绝
- **WHEN** 本人提交 title 有效字符数 < 5 的 PRD
- **THEN** 系统 MUST 返回错误码 `30002`（PRD_CONTENT_TOO_SHORT），错误消息含 "title"、当前字数与阈值；PRD 状态 MUST 保持 DRAFT

#### Scenario: content 太短被拒绝
- **WHEN** 本人提交 content 有效字符数 < 200 的 PRD
- **THEN** 系统 MUST 返回错误码 `30002`（PRD_CONTENT_TOO_SHORT），错误消息含 "content"、当前字数与阈值；PRD 状态 MUST 保持 DRAFT

#### Scenario: 缺少必要章节被拒绝
- **WHEN** 本人提交内容字数达标但只含 0 或 1 个核心章节的 PRD
- **THEN** 系统 MUST 返回错误码 `30003`（PRD_MISSING_REQUIRED_SECTION），错误消息明确列出缺失的章节名（中文显示名）；PRD 状态 MUST 保持 DRAFT

---

---

### Requirement: PRD 数据库迁移脚本
系统 SHALL 提供 Flyway 迁移脚本 `V3__create_prd_tables.sql`，创建 `prd` 表和 `prd_version` 表。

#### Scenario: 迁移脚本幂等执行
- **WHEN** Flyway 执行 V3 脚本
- **THEN** `prd` 表 MUST 存在，包含字段：`source_url`（VARCHAR 2048，nullable）、`status`（含 INITIALIZING 值）、`version`（乐观锁）、`deleted`（逻辑删除）；`prd_version` 表 MUST 包含 `source_url` 字段

---

### Requirement: PRD 提交输入门槛
系统 SHALL 在 `POST /api/v1/prds/{id}/submit` 用例中、状态机转移之前,执行下列输入门槛校验。

**字数门槛(`PRD_CONTENT_TOO_SHORT`, 30002):**
- `title` 的有效字符数(去除全部空白后)SHALL ≥ 5
- `content` 的有效字符数(去除全部空白后)SHALL ≥ 200
- 错误消息 SHALL 包含字段名(title/content)、当前字符数与阈值字符数

**章节门槛(`PRD_MISSING_REQUIRED_SECTION`, 30003):**
- 系统 SHALL 扫描 content 中的 markdown 一级/二级标题(`# xxx` / `## xxx`),抽取标题文本
- 系统 SHALL 与三个 RequiredSection 同义词集合做 **substring 包含匹配**(不区分大小写,允许首尾空白):
  - **BACKGROUND**:背景 / 概述 / 简介 / 概况 / 由来 / 缘起 / 现状 / 痛点 / 问题 / 需求来源 / 项目背景 / 立项 / background / overview / context / introduction / intro / motivation / problem
  - **GOAL**:目标 / 目的 / 价值 / 收益 / 预期 / 指标 / KPI / OKR / 意义 / 愿景 / 效果 / goal(s) / objective(s) / purpose / vision / outcome / metric(s)
  - **DESIGN**:功能 / 设计 / 方案 / 实现 / 流程 / 需求 / 详细 / 规范 / 交互 / 逻辑 / 结构 / 架构 / 组成 / 细节 / design / solution / feature(s) / implementation / flow / specification / spec / requirement(s) / architecture / module
- 至少命中 2 个 RequiredSection 才通过;否则抛 `PRD_MISSING_REQUIRED_SECTION`
- 错误消息 SHALL 列出**缺失**的 RequiredSection 中文显示名

#### Scenario: substring 匹配 — 序号前缀
- **WHEN** content 含 `## 一、需求背景` 与 `## 二、目标用户`
- **THEN** 系统 MUST 识别 BACKGROUND(含 "背景")与 GOAL(含 "目标"),通过门槛

#### Scenario: substring 匹配 — 同义词扩展
- **WHEN** content 含 `## 用户痛点` 与 `## 关键指标`
- **THEN** 系统 MUST 识别 BACKGROUND(含 "痛点")与 GOAL(含 "指标"),通过门槛

#### Scenario: 字数有效字符不含空白
- **WHEN** title="     hi     "(连尾共 13 字符,但有效仅 2 字符)调 submit
- **THEN** 系统 MUST 返回 `PRD_CONTENT_TOO_SHORT`,错误消息提示当前 2 字符 < 阈值 5

#### Scenario: 缺失章节错误消息列出中文名
- **WHEN** content 仅含 `# 背景` 一个章节,字数达标
- **THEN** 系统 MUST 返回 `PRD_MISSING_REQUIRED_SECTION`,错误消息 MUST 含 "目标" 与 "功能设计"(缺失项)

---

### Requirement: 从文件创建 PRD
系统 SHALL 提供 `POST /api/v1/prds/from-file` 接口（multipart/form-data，字段名 `file`），支持上传 PDF / Word / Markdown / 纯文本，自动解析为文本后调 AI 摘要，落到 DRAFT 状态。需要登录；同步返回 `PrdResponse`。

**支持的文件类型**：
- `application/pdf`
- `application/vnd.openxmlformats-officedocument.wordprocessingml.document`（.docx）
- `application/msword`（.doc）
- `text/plain`（.txt）
- `text/markdown` / `text/x-markdown`（.md）

类型检测 SHALL 基于 Tika 的内容魔数 + 文件名双重判定，不只看后缀。

**文件大小**：单文件 SHALL ≤ 10 MB；Spring `multipart.max-file-size` 与 service 层各拦一道，超限抛 `PRD_FILE_TOO_LARGE`。

**错误码**：
- `PRD_FILE_TYPE_UNSUPPORTED(30006)` — MIME 不在白名单
- `PRD_FILE_TOO_LARGE(30007)` — 文件超 10 MB
- `PRD_FILE_PARSE_FAILED(30008)` — Tika 解析失败 / 解析后文本过短（疑似扫描件无文字层）

#### Scenario: 上传 PDF 成功创建 PRD
- **WHEN** 已登录用户上传一份内容正常的 PDF（≥ 100 字符可识别文字）
- **THEN** 系统 MUST 同步返回 `PrdResponse`，`status=DRAFT`，`title` 与 `content` 由 AI 摘要填充；`sourceUrl` MUST 为 null（区别于 URL 路径）

#### Scenario: 上传 Word 文档成功创建 PRD
- **WHEN** 已登录用户上传一份 `.docx` 文件
- **THEN** 系统 MUST 解析为文本后 AI 摘要，落到 DRAFT 状态

#### Scenario: 上传纯文本 / Markdown 成功创建 PRD
- **WHEN** 已登录用户上传 `.txt` 或 `.md`
- **THEN** 系统 MUST 直接把文本送 AI 摘要，落到 DRAFT 状态

#### Scenario: 不支持的文件类型被拒绝
- **WHEN** 已登录用户上传 `.exe` / `.zip` / `.pptx` 等不在白名单的文件
- **THEN** 系统 MUST 返回 `PRD_FILE_TYPE_UNSUPPORTED(30006)`，错误消息含检测到的 MIME 类型

#### Scenario: 文件超 10 MB 被拒绝
- **WHEN** 已登录用户上传 > 10 MB 的文件
- **THEN** 系统 MUST 返回 `PRD_FILE_TOO_LARGE(30007)`（来自 Spring 拦截或 service 层兜底）

#### Scenario: 解析失败（疑似扫描件 / 损坏文件）
- **WHEN** 已登录用户上传一份扫描件 PDF（无文字层）或损坏的 docx
- **THEN** 系统 MUST 返回 `PRD_FILE_PARSE_FAILED(30008)`，错误消息提示"可能是扫描件，请提供文字版"或"文件无法解析"

#### Scenario: 未登录上传被拒绝
- **WHEN** 未携带 JWT 调用上传接口
- **THEN** 系统 MUST 返回 `UNAUTHORIZED(20001)`

#### Scenario: 上传后再 submit 走 #6 输入门槛
- **WHEN** 上传成功落 DRAFT 后，用户直接 POST submit（不补章节）
- **THEN** 系统 MUST 与手动/URL 路径同样过 `#6` 输入门槛校验，可能因缺章节返回 `PRD_MISSING_REQUIRED_SECTION(30003)`

---

### Requirement: PRD 前端列表页对接后端 API
前端 `#page-list` 页 SHALL 在进入时调 `GET /api/v1/prds?page=1&size=20` 拉取当前用户可见的 PRD 列表,删除原型写死数据,改为动态渲染。

- 列表 SHALL 按返回的 `items` 顺序渲染 `prd-item` 卡片,每张卡片含 title、createdAt(YYYY-MM-DD)、status 徽标
- status 徽标 SHALL 用对应颜色:DRAFT=灰、SUBMITTED=蓝、UNDER_REVIEW=橙、APPROVED=绿、REJECTED=红
- 卡片点击 SHALL 跳转到 `#page-edit` 并传 id,加载详情进编辑器
- 顶部 `stat-card` SHALL 真实展示 total / 已通过(APPROVED) / 评审中(UNDER_REVIEW) / 草稿(DRAFT) 计数
- 空列表 SHALL 显示友好提示 "暂无方案,点击右上角新建" + 不显示 stat-card

#### Scenario: ADMIN/SUBMITTER 进入列表页看到自己的 PRD
- **WHEN** 登录后点击侧边栏 "我的方案"
- **THEN** 系统 MUST 拉取后端列表 API,把每条 PRD 渲染为 `prd-item` 卡片,显示 title / 创建日期 / status 徽标

#### Scenario: 列表为空时显示占位
- **WHEN** 当前用户没有任何可见 PRD
- **THEN** 系统 MUST 显示 "暂无方案" 占位,不显示假统计数字

#### Scenario: 点击卡片进入编辑
- **WHEN** 用户点击列表里某条 PRD 卡片
- **THEN** 系统 MUST 切到 `#page-edit`,自动调 `GET /api/v1/prds/{id}` 回填 title + content,允许编辑

---

### Requirement: PRD 前端编辑器对接后端 CRUD
前端 `#page-edit` SHALL 改为真实表单(title + content textarea),按访问路径决定模式:

- 进入路径 1:列表点击 `#page-edit?id=N` → 加载 N → 编辑模式
- 进入路径 2:新建 "+ 新建方案" 按钮 → 空表单 → 新建模式
- 进入路径 3:`#7` 文件上传成功跳转 `#page-edit?id=N`(自动回填 AI 摘要的 title/content)

操作按钮:
- `保存草稿` SHALL 调 POST(新建)或 PUT(更新,带 version 乐观锁)`/api/v1/prds[/{id}]`
- `提交评审` SHALL 调 POST `/api/v1/prds/{id}/submit`;若返回 30002/30003 错误码 SHALL 用 alert 显示错误消息(含 #6 校验的具体细节)
- `删除草稿`(仅 DRAFT 状态可见)SHALL 调 DELETE `/api/v1/prds/{id}`,确认后逻辑删除
- `取消` SHALL 返回列表页

非 DRAFT 状态的 PRD(SUBMITTED/UNDER_REVIEW/APPROVED/REJECTED)SHALL 显示为只读视图,无保存/提交按钮。

#### Scenario: 新建空白草稿
- **WHEN** 用户点击 "+ 新建方案",填 title="测试 PRD",content="# 背景..." 后点保存
- **THEN** 系统 MUST 调 POST /api/v1/prds,成功后跳回列表页能看到新条目

#### Scenario: 编辑现有草稿
- **WHEN** 用户从列表点击进入一个 DRAFT 状态的 PRD,修改 title/content 后保存
- **THEN** 系统 MUST 调 PUT /api/v1/prds/{id} 带 version,成功后保留在编辑页;version 自增

#### Scenario: 提交评审被 #6 拦截
- **WHEN** 用户对一份缺章节的 DRAFT 点 "提交评审"
- **THEN** 系统 MUST 调 submit,后端返回 30003,前端 MUST 弹 alert 显示 "缺少必要章节:目标, 功能设计 (已识别:背景)"

#### Scenario: 上传文档后跳编辑页
- **WHEN** 用户在编辑页点 📎 上传 PDF/Word,后端解析 + AI 摘要返回 prdId
- **THEN** 系统 MUST 自动 `location.hash = '#edit?id=' + prdId`,编辑器自动加载该 PRD 回填 title + content

---

### Requirement: PRD 评审流程页接通后端
前端 4 个评审流程页(选评审员 / 定评审风格 / 提交评审 / 评审详情)SHALL 接通真实后端数据,删除原型写死内容。

- **选评审员**(`#agent?id=N`):调 `GET /api/v1/reviewers?enabled=true` 拉评审员列表,多选并写 localStorage `prd_agents_<id>`(JSON 数组);未选至少 1 位时下一步按钮拦截
- **定评审风格**(`#style?id=N`):调 `GET /api/v1/review-styles` 拉风格列表,单选并写 localStorage `prd_style_<id>`;首次进入默认选中 `isDefault=true` 的风格
- **提交评审**(`#precheck?id=N`):调 `GET /api/v1/prds/{id}` 拉真实 PRD title+content + localStorage 已选评审员/风格,展示完整确认面板;点"确认提交评审"调真后端 `POST /api/v1/prds/{id}/submit`,#6 输入门槛失败时把错误消息透传给用户
- **评审详情**(`#review?id=N`):列表点击 SUBMITTED+ 状态的 PRD 跳此页;展示只读 PRD 内容 + 已选评审员/风格摘要 + 状态徽标 + 顶部进度栏 4 步全 ✓

#### Scenario: 选评审员页拉真实数据
- **WHEN** 进入 `#agent?id=N` 页
- **THEN** 系统 MUST 调 `GET /api/v1/reviewers?enabled=true&page=1&size=100`,把返回的 items 渲染为可点选卡片

#### Scenario: 提交评审触发真后端
- **WHEN** 用户在 `#precheck?id=N` 页点"✓ 确认提交评审"
- **THEN** 系统 MUST 调 `POST /api/v1/prds/{id}/submit`,成功后 PRD 状态变 SUBMITTED,跳回列表

#### Scenario: 评审详情展示提交前快照
- **WHEN** 用户从列表点击一个 SUBMITTED 状态的 PRD
- **THEN** 系统 MUST 跳到 `#review?id=N`,展示只读 PRD 内容,以及 localStorage 中该 PRD 的评审员/风格选择

---

### Requirement: PRD 已提交评审后流程锁定
后端 PRD 状态进入 SUBMITTED / UNDER_REVIEW / APPROVED / REJECTED 之一后,前端 SHALL 锁定所有修改入口。

- 编辑器加载非 DRAFT 状态 PRD:title/content 输入框 `disabled=true`;「💾 保存草稿」「🗑️ 删除草稿」「下一步」按钮全隐藏
- 流程页(`#agent` / `#style` / `#precheck`)入口防护:进入前检查 PRD 状态,非 DRAFT 弹错并自动跳转 `#review?id=N` 评审详情页
- 列表点击行为分流:DRAFT/INITIALIZING 进 `#edit?id=N` 编辑器,其他状态进 `#review?id=N` 评审详情

#### Scenario: 已提交 PRD 编辑器只读
- **WHEN** 用户进入 `#edit?id=N`,且该 PRD status=SUBMITTED
- **THEN** 编辑器输入框 MUST disabled,「下一步」「保存」「删除」按钮 MUST 隐藏

#### Scenario: 已提交 PRD 流程页弹错跳转
- **WHEN** 用户直接 URL 输入 `#agent?id=N`,且该 PRD status≠DRAFT
- **THEN** 系统 MUST 弹错"已是 X 状态,不能再修改流程配置",自动跳转 `#review?id=N`

---

### Requirement: PRD id 通过 URL hash 持久化
前端流程页 SHALL 用 URL hash query 参数承载 PRD id(`#agent?id=N` / `#style?id=N` / `#precheck?id=N` / `#review?id=N`),不依赖 DOM 隐藏字段。

`currentPrdId()` 解析优先级:URL hash query → DOM hidden input(兜底)。这保证:
- 用户刷新页面后流程状态不丢
- 直接 URL 输入能正确恢复
- localStorage 写入 key 永远不会变成 `prd_agents_null` / `prd_style_null` 等废 key

#### Scenario: 刷新流程页 PRD id 保留
- **WHEN** 用户在 `#agent?id=5` 页按 F5 刷新
- **THEN** 页面 MUST 恢复到 agent 页且当前 PRD id 仍为 5,localStorage 写入仍指向 PRD 5

---
