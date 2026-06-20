# Review Styles

## Purpose

评审风格是评审任务的"深度/态度"开关，与"评审员（角色视角）"正交。同一份 PRD、同一位评审员，可按项目阶段（小迭代 vs 大版本发版）选不同风格输出不同深度的评审。

ADMIN 维护风格的 CRUD 与默认风格切换，普通用户在发起评审时从启用的风格中挑选；系统始终保证"恰好 1 个默认风格"作为兜底。

后续 change（#15 Prompt Composer）会把"评审员模板 × 风格规则 × RAG 上下文"拼装成最终 Prompt——风格只负责描述"深度/态度"维度，不进入评审员模板。

## Requirements


### Requirement: ReviewStyle 聚合根
系统 SHALL 维护 `ReviewStyle` 聚合根，包含以下字段：`id`（Long，自增主键）、`name`（名称，VARCHAR(50)，NOT NULL，唯一）、`icon`（emoji 图标，VARCHAR(20)，nullable）、`scenario`（适用场景描述，VARCHAR(200)，nullable）、`rules`（规则列表，TEXT，存储 JSON 字符串，4–8 条 label-content 对）、`enabled`（Boolean，默认 true，是否在发起评审时可见）、`isDefault`（Boolean，默认 false，是否为系统默认风格）、`sortOrder`（Integer，默认 0，升序排列）、`version`（Integer，乐观锁，从 1 开始）、`deleted`（逻辑删除标记，默认 0）、`createdAt`（创建时间）、`updatedAt`（更新时间）。

聚合根 SHALL 为纯 Java 对象（无 MyBatis 注解），通过静态工厂方法创建，内部封装所有领域行为；rules 字段在内存中表现为 `List<StyleRule>`，每个 `StyleRule` 为不可变 record（含 `label` 和 `content`）。

#### Scenario: 创建风格字段完整性
- **WHEN** ADMIN 提供 name、icon、scenario、rules 创建评审风格
- **THEN** `enabled` MUST 为 `true`，`isDefault` MUST 为 `false`，`sortOrder` MUST 为 `0`，`version` MUST 为 `1`，`deleted` MUST 为 `0`

#### Scenario: 重建评审风格
- **WHEN** 从持久化层加载评审风格数据
- **THEN** 系统 MUST 通过 `reconstruct` 静态方法还原所有字段，包括 id、时间戳、version、isDefault

---

### Requirement: 规则数量与字段校验
系统 SHALL 在创建和更新评审风格时校验规则列表：规则数量 MUST 在 4 到 8 条之间（含两端），每条规则的 `label` 和 `content` MUST 非空字符串。违反时 SHALL 拒绝保存并抛出 `STYLE_RULE_INVALID` 错误。

#### Scenario: 规则数量过少
- **WHEN** ADMIN 提交的 rules 数量少于 4 条
- **THEN** 系统 MUST 抛出 `STYLE_RULE_INVALID` 错误

#### Scenario: 规则数量过多
- **WHEN** ADMIN 提交的 rules 数量超过 8 条
- **THEN** 系统 MUST 抛出 `STYLE_RULE_INVALID` 错误

#### Scenario: 规则字段为空
- **WHEN** ADMIN 提交的 rules 中存在 label 或 content 为空字符串/null 的项
- **THEN** 系统 MUST 抛出 `STYLE_RULE_INVALID` 错误

#### Scenario: 合法规则保存成功
- **WHEN** ADMIN 提交的 rules 数量在 4–8 条之间且每条 label、content 均非空
- **THEN** 保存 MUST 成功

---

### Requirement: 评审风格名称唯一性
系统 SHALL 确保评审风格名称在未删除的风格中唯一（逻辑删除的记录不参与唯一性约束）。

#### Scenario: 名称重复被拒绝
- **WHEN** ADMIN 创建或更新评审风格，使用的 name 已被另一个未删除的风格占用
- **THEN** 系统 MUST 拒绝操作并返回 `DATA_CONFLICT` 错误

#### Scenario: 与已删除风格同名允许创建
- **WHEN** ADMIN 创建评审风格，使用的 name 仅被已逻辑删除的风格占用
- **THEN** 创建 MUST 成功

---

### Requirement: 默认风格唯一性不变量
系统 SHALL 永远恰好维护 1 个 `isDefault=true` 且 `enabled=true` 的评审风格，作为用户发起评审时的默认选项。

切换默认风格 SHALL 仅通过专用接口 `POST /api/v1/review-styles/{id}/set-default` 完成；该接口 SHALL 在单个事务内将所有其他风格的 `isDefault` 置为 `false`，再将目标风格的 `isDefault` 置为 `true`。

普通创建接口 SHALL 禁止将新风格创建为 `isDefault=true`（请求体即使携带该字段也忽略，永远保存为 `false`）。普通更新接口 SHALL 禁止修改 `isDefault` 字段。

#### Scenario: 通过专用接口切换默认风格
- **WHEN** ADMIN 调用 `POST /api/v1/review-styles/{id}/set-default`，目标风格已启用
- **THEN** 系统 MUST 在事务内将所有其他风格的 `isDefault` 置为 `false`，将目标风格的 `isDefault` 置为 `true`

#### Scenario: 设为默认的目标必须已启用
- **WHEN** ADMIN 调用 set-default 接口，目标风格 `enabled=false`
- **THEN** 系统 MUST 拒绝操作（禁用风格不能成为默认）并返回参数校验错误

#### Scenario: 普通创建忽略 isDefault
- **WHEN** ADMIN 通过 `POST /api/v1/review-styles` 创建风格，请求体含 `isDefault=true`
- **THEN** 系统 MUST 忽略该字段，新风格的 `isDefault` 始终为 `false`

#### Scenario: 普通更新忽略 isDefault
- **WHEN** ADMIN 通过 `PUT /api/v1/review-styles/{id}` 更新风格，请求体含 `isDefault` 字段
- **THEN** 系统 MUST 忽略该字段，`isDefault` 保持原值不变

---

### Requirement: 默认风格保护
系统 SHALL 禁止删除或禁用当前的默认风格，确保发起评审时始终存在可用的默认选项。

#### Scenario: 默认风格不可删除
- **WHEN** ADMIN 尝试删除 `isDefault=true` 的风格
- **THEN** 系统 MUST 拒绝操作并返回 `STYLE_DEFAULT_NOT_DELETABLE` 错误

#### Scenario: 默认风格不可禁用
- **WHEN** ADMIN 尝试将 `isDefault=true` 的风格 `enabled` 置为 `false`
- **THEN** 系统 MUST 拒绝操作并返回 `STYLE_DEFAULT_NOT_DISABLABLE` 错误

#### Scenario: 切换默认后可删除原风格
- **WHEN** ADMIN 先将某风格通过 set-default 切换为默认，再删除原默认风格
- **THEN** 删除 MUST 成功（原风格已不是默认）

---

### Requirement: 评审风格 CRUD API
系统 SHALL 提供 RESTful API（`/api/v1/review-styles`）支持评审风格的增删改查。

写操作（创建、更新、删除、设为默认）SHALL 仅限 ADMIN 角色。读操作（列表、详情）SHALL 对所有已登录用户开放。

#### Scenario: ADMIN 创建评审风格
- **WHEN** ADMIN 发送 `POST /api/v1/review-styles`，body 含 name、icon、scenario、rules、sortOrder
- **THEN** 系统 MUST 返回创建成功的风格信息，HTTP 200

#### Scenario: ADMIN 更新评审风格
- **WHEN** ADMIN 发送 `PUT /api/v1/review-styles/{id}`，body 含 name、icon、scenario、rules、enabled、sortOrder、version
- **THEN** 系统 MUST 执行乐观锁更新并返回更新后的风格信息

#### Scenario: 乐观锁冲突
- **WHEN** ADMIN 更新风格时提交的 version 与数据库当前 version 不一致
- **THEN** 系统 MUST 返回 `DATA_CONFLICT` 错误

#### Scenario: ADMIN 删除评审风格
- **WHEN** ADMIN 发送 `DELETE /api/v1/review-styles/{id}`，目标非默认风格
- **THEN** 系统 MUST 执行逻辑删除（设置 deleted=1），HTTP 200

#### Scenario: 查看评审风格详情
- **WHEN** 已登录用户发送 `GET /api/v1/review-styles/{id}`
- **THEN** 系统 MUST 返回风格信息（含解析后的 rules 列表）；若 id 不存在或已删除，返回 `STYLE_NOT_FOUND`

#### Scenario: 非 ADMIN 尝试写操作
- **WHEN** SUBMITTER 或 TEAM_MEMBER 尝试 POST/PUT/DELETE/set-default
- **THEN** 系统 MUST 返回 `FORBIDDEN` 错误

---

### Requirement: 评审风格列表查询
系统 SHALL 提供分页列表接口 `GET /api/v1/review-styles`，支持按 `enabled` 状态筛选。

列表 SHALL 按 `sortOrder ASC, id ASC` 排序。ADMIN 可查看所有评审风格（含禁用的），非 ADMIN 仅查看 `enabled=true` 的风格。

#### Scenario: ADMIN 查看全部风格
- **WHEN** ADMIN 发送 `GET /api/v1/review-styles`（无 enabled 筛选参数）
- **THEN** 系统 MUST 返回所有未删除的风格，含启用和禁用的

#### Scenario: ADMIN 按启用状态筛选
- **WHEN** ADMIN 发送 `GET /api/v1/review-styles?enabled=true`
- **THEN** 系统 MUST 仅返回 `enabled=true` 的风格

#### Scenario: 非 ADMIN 查看列表
- **WHEN** SUBMITTER 发送 `GET /api/v1/review-styles`
- **THEN** 系统 MUST 仅返回 `enabled=true` 的风格，忽略 `enabled` 参数

#### Scenario: 列表排序
- **WHEN** 查询评审风格列表
- **THEN** 结果 MUST 按 `sortOrder ASC, id ASC` 排序

---

### Requirement: 种子评审风格
系统 SHALL 通过迁移脚本预置 4 个默认评审风格，与前端原型 `frontend/app.html` 的"评审风格"页面保持一致：

1. **宽松**（icon=`⚡`，sortOrder=10，enabled=true，isDefault=false，4 条规则）：早期探索、快速验证场景
2. **务实**（icon=`🎯`，sortOrder=20，enabled=true，isDefault=false，5 条规则）：MVP 与小版本迭代场景
3. **标准**（icon=`📋`，sortOrder=30，enabled=true，**isDefault=true**，6 条规则）：日常评审默认场景
4. **严谨**（icon=`🔬`，sortOrder=40，enabled=true，isDefault=false，7 条规则）：大版本发版、关键决策场景

每个种子风格 SHALL 配备符合 4–8 条数量约束的规则列表，规则内容与前端原型展示一致。

#### Scenario: 新环境部署后种子数据可用
- **WHEN** 应用首次启动并执行迁移
- **THEN** `review_style` 表 MUST 包含 4 条记录，全部 `enabled=true`，仅"标准"`isDefault=true`，`sortOrder` 分别为 `10/20/30/40`

#### Scenario: 默认种子风格满足不变量
- **WHEN** 种子数据加载完毕
- **THEN** 系统 MUST 满足"恰好 1 个 isDefault=true 且 enabled=true"的不变量

#### Scenario: 种子风格可被编辑
- **WHEN** ADMIN 修改种子风格的规则列表
- **THEN** 更新 MUST 成功，系统不对种子数据做特殊保护（删除/禁用仍受默认风格保护规则约束）

---

### Requirement: 前端评审风格管理页面
系统 SHALL 在 `frontend/app.html` 的"评审风格"页面通过后端 API 动态加载并管理评审风格，不再使用硬编码静态卡片。

页面 SHALL 提供以下能力：
- 进入页面时调用 `GET /api/v1/review-styles` 加载风格列表，按 `sortOrder ASC, id ASC` 顺序渲染卡片（含 icon、name、isDefault 徽标、enabled 徽标、scenario、规则数量预览）
- ADMIN 用户 SHALL 看到"+ 新建风格"、"✏️ 编辑"、"⭐ 设为默认"、"启用/停用"、"🗑️ 删除"按钮；非 ADMIN 用户 SHALL 仅看到只读卡片
- "新建"和"编辑" Modal SHALL 提供名称、图标、适用场景、规则列表（动态增删行，每行 label + content）、排序权重输入项；Modal 不含 `isDefault` 字段
- 更新操作 SHALL 携带当前 version 字段以支持乐观锁
- 删除操作 SHALL 弹出二次确认；对默认风格点击删除 SHALL 提示"默认风格不可删除，请先切换默认风格"
- 禁用操作对默认风格 SHALL 提示"默认风格不可禁用，请先切换默认风格"
- "⭐ 设为默认"按钮在当前默认风格上 SHALL 显示为已选中态（不可点击）

#### Scenario: ADMIN 进入页面看到全部风格
- **WHEN** 已登录 ADMIN 切换到"评审风格"导航
- **THEN** 页面 MUST 显示所有 4 个风格，标准风格 MUST 显示"默认"徽标

#### Scenario: 非 ADMIN 进入页面仅看到启用的风格
- **WHEN** 已登录 SUBMITTER 切换到"评审风格"导航
- **THEN** 页面 MUST 仅显示 `enabled=true` 的风格，且不显示编辑/删除/设为默认按钮

#### Scenario: ADMIN 通过 Modal 创建新风格
- **WHEN** ADMIN 点击"+ 新建风格"、填写表单（至少 4 条规则）、保存
- **THEN** 系统 MUST 调用 `POST /api/v1/review-styles`，成功后关闭 Modal 并重新加载列表

#### Scenario: ADMIN 切换默认风格
- **WHEN** ADMIN 在非默认风格卡片点击"⭐ 设为默认"
- **THEN** 系统 MUST 调用 `POST /api/v1/review-styles/{id}/set-default`，成功后刷新列表，新默认风格显示"默认"徽标

#### Scenario: 编辑风格触发乐观锁
- **WHEN** ADMIN 通过 Modal 编辑风格
- **THEN** 请求 body MUST 包含当前风格的 version 字段
