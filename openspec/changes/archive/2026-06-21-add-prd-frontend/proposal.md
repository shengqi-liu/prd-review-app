## Why

#5 (`add-prd-storage`) 完整实现了 PRD 后端 CRUD,但前端「我的方案」列表和「编写方案」编辑器一直是**写死的原型**——展示假数据 + 按钮无 onclick + 编辑器是只读的 5 节"积分体系"文本。用户能调评审员/风格/知识库/上传文档,但**没法真正创建、编辑、提交一份 PRD**——这是当前最阻塞用户验收的缺口。

#7 上传文档落了 DRAFT 也无法在前端打开继续编辑,只是 alert 一下 PRD id 就结束。

## What Changes

- **列表页接 `GET /api/v1/prds`**:删除 4 张写死卡片,改为动态拉取 + 渲染;按状态计算 stat-card 数字;按状态过滤的 tabs 真实生效;空列表友好提示
- **编辑页接 `GET/POST/PUT /api/v1/prds`**:URL hash 参数(`#edit?id=123`)决定是编辑现有还是新建;表单含 title + content(markdown) 两个字段;保存调 POST/PUT;乐观锁 version 透传
- **新建按钮**:`＋ 新建方案` 触发 `goStep('edit')` 进入空表单
- **提交评审按钮**:编辑页底部 `提交评审` 触发 `POST /prds/{id}/submit`;走 #6 输入门槛校验失败时弹错(显示 30002/30003 友好消息)
- **文件上传衔接**:`prd-upload.js` 上传成功后跳转到编辑页 `#edit?id=<newPrdId>` 让用户预览 AI 摘要、补章节、再提交
- **删除按钮**:编辑页加 `删除草稿` 按钮(仅 DRAFT 可见),调 `DELETE /prds/{id}`

## Capabilities

### New Capabilities

(无)

### Modified Capabilities

- `prd-storage`:前端 UI 真正对接后端 CRUD 与 submit 用例;不涉及后端契约变化

## Impact

- **前端**:
  - `frontend/app.html` — 重写 `#page-list`(动态容器 + 真实 stat/tabs)与 `#page-edit`(title 输入框 + content textarea + 保存/提交/删除 按钮);保留侧面板"PRD 模板"等纯展示
  - `frontend/js/prd.js` — **新增**模块,封装 loadPrds / renderList / openEditor / saveDraft / submitForReview / deleteDraft / handleHashRoute
  - `frontend/js/prd-upload.js` — 上传成功后改为跳转编辑页(`location.hash = '#edit?id=' + id`)
  - 末尾引入 `<script src="js/prd.js"></script>`,在 `navTo('list')` 加 `loadPrds()` 钩子、`navTo('edit')` 加 `openEditor(id?)` 钩子
- **无后端改动**(API 全部已就绪,本 change 纯前端补丁)
- **无 DB schema 变更**
- **样式**:复用已有 `prd-item` / `editor-box` / `editor-area` 等类,只把内容由静态改为动态

## Out of Scope

- 不做评审流程页面接通(选评审员/选风格/内审/评审中/报告 = `#page-agent` `#page-style` `#page-precheck` `#page-reviewing` `#page-report` 全部保持原型),因为后端评审编排 #17–#21 还没做
- 不做编辑器的富文本工具栏接通(B/I/U/H1/H2 按钮)— content 字段直接用 textarea 接受 markdown 即可
- 不做"完整性检查"侧面板的实时反馈(可由 #6 校验在 submit 时统一报错)
- 不做搜索框 / 评审员筛选 / 风格筛选(后端列表 API 不支持这些过滤,先保留 UI 占位)
- 不做评审管理表格(`#page-review-manage`)
- 不做自动保存(用户主动点保存即可)
- 不做版本历史展示(`prd_version` 表后端有,前端 UI 等评审历史 page 一起做)
