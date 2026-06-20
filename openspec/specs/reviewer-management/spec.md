# Reviewer Management

## Purpose

AI 评审员是评审流水线的核心配置单元，每位评审员封装一种评审视角（角色定位 + Prompt 模板）。ADMIN 维护评审员的 CRUD 生命周期，普通用户在发起评审时从启用的评审员中挑选。

评审员模板的职责边界：只描述"评审员的角色与如何评审 PRD 内容"。运行时数据（评审风格、知识库检索结果）由后续 change 的 Prompt Composer 在拼装阶段从外层注入，不进入评审员模板本身。

## Requirements

### Requirement: Reviewer 聚合根
系统 SHALL 维护 `Reviewer` 聚合根，包含以下字段：`id`（Long，自增主键）、`name`（名称，VARCHAR(100)，NOT NULL，唯一）、`icon`（展示图标 emoji，VARCHAR(20)，nullable，用于前端视觉识别）、`description`（角色描述，VARCHAR(500)，说明该评审员的评审视角与专长）、`promptTemplate`（Prompt 模板，TEXT，NOT NULL，支持 `{{variable}}` 占位符）、`enabled`（Boolean，默认 true，是否在评审选择列表中可见）、`sortOrder`（Integer，默认 0,升序排列）、`version`（Integer，乐观锁，从 1 开始）、`deleted`（逻辑删除标记，默认 0）、`createdAt`（创建时间）、`updatedAt`（更新时间）。

聚合根 SHALL 为纯 Java 对象（无 MyBatis 注解），通过静态工厂方法创建，内部封装所有领域行为。

#### Scenario: 创建评审员字段完整性
- **WHEN** ADMIN 提供 name、description、promptTemplate 创建评审员
- **THEN** `enabled` MUST 为 `true`，`sortOrder` MUST 为 `0`，`version` MUST 为 `1`，`deleted` MUST 为 `0`

#### Scenario: 重建评审员
- **WHEN** 从持久化层加载评审员数据
- **THEN** 系统 MUST 通过 `reconstruct` 静态方法还原所有字段，包括 id、时间戳、version

---

### Requirement: Prompt 模板占位符校验
系统 SHALL 在创建和更新评审员时校验 Prompt 模板的占位符合法性。合法占位符白名单为：`prd_title`、`prd_content`。模板中出现白名单之外的 `{{variable}}` 占位符时，系统 SHALL 拒绝保存并抛出 `REVIEWER_PROMPT_INVALID` 错误。

评审员模板只负责定义"评审员的角色与如何评审 PRD"，因此只允许 PRD 级别的占位符。评审风格（`review_style`）、知识库上下文（`kb_context`）等运行时数据，由 Prompt Composer（change #15）在拼装阶段通过外层包装注入，不进入评审员模板本身。

模板 SHALL 允许不包含任何占位符（纯文本 Prompt）。模板 SHALL 允许同一占位符出现多次。

#### Scenario: 合法模板保存成功
- **WHEN** ADMIN 保存的 promptTemplate 仅包含白名单内的占位符（如 `{{prd_title}}` 和 `{{prd_content}}`）
- **THEN** 保存 MUST 成功

#### Scenario: 非法占位符被拒绝
- **WHEN** ADMIN 保存的 promptTemplate 包含白名单外的占位符（如 `{{unknown_var}}`）
- **THEN** 系统 MUST 抛出 `REVIEWER_PROMPT_INVALID` 错误，错误信息 MUST 包含非法变量名

#### Scenario: review_style / kb_context 不属于评审员模板
- **WHEN** ADMIN 保存的 promptTemplate 包含 `{{review_style}}` 或 `{{kb_context}}`
- **THEN** 系统 MUST 抛出 `REVIEWER_PROMPT_INVALID` 错误（这两个占位符由 Prompt Composer 在拼装阶段注入，不属于评审员模板）

#### Scenario: 纯文本模板允许保存
- **WHEN** ADMIN 保存的 promptTemplate 不包含任何 `{{}}` 占位符
- **THEN** 保存 MUST 成功

#### Scenario: 模板不可为空
- **WHEN** ADMIN 提交的 promptTemplate 为空字符串或 null
- **THEN** 系统 MUST 拒绝保存（参数校验失败）

---

### Requirement: 评审员名称唯一性
系统 SHALL 确保评审员名称在未删除的评审员中唯一（逻辑删除的记录不参与唯一性约束）。

#### Scenario: 名称重复被拒绝
- **WHEN** ADMIN 创建或更新评审员，使用的 name 已被另一个未删除的评审员占用
- **THEN** 系统 MUST 拒绝操作并返回 `DATA_CONFLICT` 错误

#### Scenario: 与已删除评审员同名允许创建
- **WHEN** ADMIN 创建评审员，使用的 name 仅被已逻辑删除的评审员占用
- **THEN** 创建 MUST 成功

---

### Requirement: 评审员 CRUD API
系统 SHALL 提供 RESTful API（`/api/v1/reviewers`）支持评审员的增删改查。

写操作（创建、更新、删除）SHALL 仅限 ADMIN 角色。读操作（列表、详情）SHALL 对所有已登录用户开放。

#### Scenario: ADMIN 创建评审员
- **WHEN** ADMIN 发送 `POST /api/v1/reviewers`，body 含 name、description、promptTemplate
- **THEN** 系统 MUST 返回创建成功的评审员信息，HTTP 200

#### Scenario: ADMIN 更新评审员
- **WHEN** ADMIN 发送 `PUT /api/v1/reviewers/{id}`，body 含 name、description、promptTemplate、enabled、sortOrder、version
- **THEN** 系统 MUST 执行乐观锁更新并返回更新后的评审员信息

#### Scenario: 乐观锁冲突
- **WHEN** ADMIN 更新评审员时提交的 version 与数据库当前 version 不一致
- **THEN** 系统 MUST 返回 `DATA_CONFLICT` 错误

#### Scenario: ADMIN 删除评审员
- **WHEN** ADMIN 发送 `DELETE /api/v1/reviewers/{id}`
- **THEN** 系统 MUST 执行逻辑删除（设置 deleted=1），HTTP 200

#### Scenario: 查看评审员详情
- **WHEN** 已登录用户发送 `GET /api/v1/reviewers/{id}`
- **THEN** 系统 MUST 返回评审员信息；若 id 不存在或已删除，返回 `REVIEWER_NOT_FOUND`

#### Scenario: 非 ADMIN 尝试写操作
- **WHEN** SUBMITTER 或 TEAM_MEMBER 尝试 POST/PUT/DELETE
- **THEN** 系统 MUST 返回 `FORBIDDEN` 错误

---

### Requirement: 评审员列表查询
系统 SHALL 提供分页列表接口 `GET /api/v1/reviewers`，支持按 `enabled` 状态筛选。

列表 SHALL 按 `sortOrder ASC, id ASC` 排序。ADMIN 可查看所有评审员（含禁用的），非 ADMIN 仅查看 `enabled=true` 的评审员。

#### Scenario: ADMIN 查看全部评审员
- **WHEN** ADMIN 发送 `GET /api/v1/reviewers`（无 enabled 筛选参数）
- **THEN** 系统 MUST 返回所有未删除的评审员，含启用和禁用的

#### Scenario: ADMIN 按启用状态筛选
- **WHEN** ADMIN 发送 `GET /api/v1/reviewers?enabled=true`
- **THEN** 系统 MUST 仅返回 `enabled=true` 的评审员

#### Scenario: 非 ADMIN 查看列表
- **WHEN** SUBMITTER 发送 `GET /api/v1/reviewers`
- **THEN** 系统 MUST 仅返回 `enabled=true` 的评审员，忽略 `enabled` 参数

#### Scenario: 列表排序
- **WHEN** 查询评审员列表
- **THEN** 结果 MUST 按 `sortOrder ASC, id ASC` 排序

---

### Requirement: 种子评审员
系统 SHALL 通过迁移脚本预置 5 位默认评审员，与前端原型 `frontend/app.html` 的"AI 评审员"页面保持一致：

1. **产品顾问**（icon=`🧑‍💼`，sortOrder=10，enabled=true）：用户价值视角
2. **技术架构师**（icon=`🏗️`，sortOrder=20，enabled=true）：技术可行性视角
3. **商业分析师**（icon=`📊`，sortOrder=30，enabled=true）：ROI 与商业模式视角
4. **竞品研究员**（icon=`🔍`，sortOrder=40，enabled=true）：竞品对比视角
5. **合规风控官**（icon=`🛡️`，sortOrder=50，enabled=false）：数据安全与合规视角，默认禁用

每位种子评审员 SHALL 配备完整的 Prompt 模板，至少包含 `{{prd_title}}`、`{{prd_content}}` 占位符。

#### Scenario: 新环境部署后种子数据可用
- **WHEN** 应用首次启动并执行迁移
- **THEN** `reviewer` 表 MUST 包含 5 条记录，前 4 条 `enabled=true`，第 5 条（合规风控官）`enabled=false`，`sortOrder` 分别为 `10/20/30/40/50`

#### Scenario: 种子评审员可被编辑
- **WHEN** ADMIN 修改种子评审员的 Prompt 模板
- **THEN** 更新 MUST 成功，系统不对种子数据做特殊保护

---

### Requirement: 前端评审员管理页面
系统 SHALL 在 `frontend/app.html` 的"AI 评审员"页面通过后端 API 动态加载并管理评审员，不再使用硬编码静态卡片。

页面 SHALL 提供以下能力：
- 进入页面时调用 `GET /api/v1/reviewers` 加载评审员列表，按 `sortOrder ASC, id ASC` 顺序渲染卡片（含 icon、name、enabled 徽标、description、Prompt 摘要预览）
- ADMIN 用户 SHALL 看到"+ 新建评审员"、"✏️ 编辑 Prompt"、"启用/停用"、"🗑️ 删除"、"🧪 测试"按钮；非 ADMIN 用户 SHALL 仅看到只读卡片
- "新建"和"编辑" Modal SHALL 提供名称、图标、描述、Prompt 模板、排序权重输入项，并提供白名单占位符快捷插入按钮
- 更新操作 SHALL 携带当前 version 字段以支持乐观锁
- 删除操作 SHALL 弹出二次确认后调用 `DELETE /api/v1/reviewers/{id}`
- "🧪 测试"按钮 SHALL 点亮（不再 disabled），点击后弹出试跑 Modal（详见 reviewer-testing capability）

#### Scenario: ADMIN 进入页面看到全部评审员
- **WHEN** 已登录 ADMIN 切换到"AI 评审员"导航
- **THEN** 页面 MUST 显示所有 5 位评审员，含禁用的合规风控官（透明度降低）

#### Scenario: 非 ADMIN 进入页面仅看到启用的评审员
- **WHEN** 已登录 SUBMITTER 切换到"AI 评审员"导航
- **THEN** 页面 MUST 仅显示 enabled=true 的 4 位评审员，且不显示编辑/删除/测试按钮

#### Scenario: ADMIN 通过 Modal 创建新评审员
- **WHEN** ADMIN 点击"+ 新建评审员"、填写表单、保存
- **THEN** 系统 MUST 调用 `POST /api/v1/reviewers`，成功后关闭 Modal 并重新加载列表

#### Scenario: 编辑评审员触发乐观锁
- **WHEN** ADMIN 通过 Modal 编辑评审员
- **THEN** 请求 body MUST 包含当前评审员的 version 字段

#### Scenario: ADMIN 点击测试按钮
- **WHEN** ADMIN 点击评审员卡片的"🧪 测试"按钮
- **THEN** 系统 MUST 弹出试跑 Modal（具体行为详见 reviewer-testing capability）
