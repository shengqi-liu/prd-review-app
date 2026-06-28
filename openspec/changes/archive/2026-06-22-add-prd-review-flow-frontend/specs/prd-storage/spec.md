## ADDED Requirements

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

## MODIFIED Requirements

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
