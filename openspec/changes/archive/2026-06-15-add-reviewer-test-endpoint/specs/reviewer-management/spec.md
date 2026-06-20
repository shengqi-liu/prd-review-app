## MODIFIED Requirements

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
