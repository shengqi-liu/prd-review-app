## ADDED Requirements

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
