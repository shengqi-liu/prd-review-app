## 1. 前端 — page-list 改造

- [x] 1.1 把 `app.html` 里 `#page-list` 内部的 4 张写死卡片 + 写死 stat-card 数字 + tabs 改为动态容器:
  - `.stat-grid` 内部清空,加 `id="prd-stats"`
  - `.prd-list` 内部清空,加 `id="prd-list-container"`
  - 保留 section-title / 新建按钮 / search-bar(后端暂不支持,保留 UI 占位即可,搜索/筛选不接)
- [x] 1.2 "+ 新建方案" 按钮 onclick 改为 `goCreatePrd()`(在 prd.js 实现:`location.hash = '#edit'`)

## 2. 前端 — page-edit 改造

- [x] 2.1 替换 `#page-edit` 的 `editor-layout` 为新的表单结构:
  - title 输入 `<input id="prd-edit-title" ...>`
  - content textarea `<textarea id="prd-edit-content" rows=24 ...>` 用等宽字体
  - 隐藏字段保留:`<input id="prd-edit-id" type="hidden">` `<input id="prd-edit-version" type="hidden">`
- [x] 2.2 替换 `bottom-bar` 按钮:
  - `取消` → 回列表 `navTo('list',null)`
  - `保存草稿` onclick=`saveDraft()`,id=`prd-save-btn`
  - `提交评审` onclick=`submitForReview()`,id=`prd-submit-btn`(仅 DRAFT 显示)
  - `删除草稿` onclick=`deleteDraft()`,id=`prd-delete-btn`(仅已存在 DRAFT 显示)
- [x] 2.3 把侧面板 "完整性检查" 改为只展示 #6 校验说明(纯静态文案,不实时检查),"PRD 模板" 保留装饰

## 3. 前端 — 新建 prd.js 模块

- [x] 3.1 创建 `frontend/js/prd.js`,内容:
  - 常量 `PRD_API='/api/v1/prds'`、`STATUS_BADGE` 映射、`currentEditPrd`(闭包变量)
  - `async function loadPrds()` — 调 `GET /api/v1/prds?page=1&size=100` → 渲染列表 + 统计
  - `function renderPrdList(items)` — 输出 `prd-item` 卡片 HTML 到 `#prd-list-container`,空时占位
  - `function renderPrdStats(items)` — 计算各 status 数量,塞到 `#prd-stats`
  - `function openPrdEditor(id)` — id 为 null 则新建,否则 `GET /api/v1/prds/{id}` 回填表单 + 显示/隐藏 删除按钮
  - `async function saveDraft()` — 读表单,验证 title/content 非空;有 id 走 PUT 否则 POST;成功 alert + 留在编辑页(回填新 version);失败 alert 错误消息
  - `async function submitForReview()` — 必须先 saveDraft 持久化最新内容(避免提交旧版),再调 POST `/submit`;失败把 30002/30003/30005 的 message 直接 alert
  - `async function deleteDraft()` — 弹 confirm,调 DELETE → 跳列表
  - `function goCreatePrd()` — `location.hash = '#edit'` + 触发 router
  - `function handleHashRoute()` — 解析 `#edit?id=N`,调 navTo + openPrdEditor
  - 末尾 `window.xxx = xxx;` 暴露所有给 onclick 用的函数
  - 监听 `window.addEventListener('hashchange', handleHashRoute)` + 初次加载触发一次

## 4. 前端 — 接入主框架

- [x] 4.1 在 `app.html` 末尾 `<script src="js/kb-repository.js"></script>` 后加 `<script src="js/prd.js"></script>` 和 `<script src="js/prd-upload.js"></script>`(prd-upload.js 应在 prd.js 之后,因为它会调 location.hash)
- [x] 4.2 在 `navTo()` 函数末尾(已有 reviewer / style / kb 加载钩子的地方)加:
  ```js
  if (name === 'list' && typeof loadPrds === 'function') loadPrds();
  ```
- [x] 4.3 在 navTo 切到 edit 页时,如果 hash 里没有 id,认为是新建,清空表单;有 id 则触发 openPrdEditor。这部分逻辑直接放 hashchange handler 处理更干净,navTo 进 edit 不强制触发(避免双重加载)

## 5. 前端 — 文件上传衔接修改

- [x] 5.1 修改 `prd-upload.js` 里 `uploadPrdFile` 成功后的逻辑:
  - 移除"alert + navTo('list')"
  - 改为:`alert('上传成功,跳转到编辑页')` + `location.hash = '#edit?id=' + prd.id;`
  - hash 变更会触发 router → navTo('edit') + openPrdEditor(id)

## 6. 集成验证(手工)

- [x] 6.1 进入 "我的方案",看到空列表 + "暂无方案" 占位(因为前面验证清空了数据)
- [x] 6.2 点 "+ 新建方案" → 输入 title="测试方案" + content="# 背景\n这是测试..." → 保存 → 提示成功 → 回列表能看到新卡片
- [x] 6.3 点击新建的卡片 → 进编辑页 → 修改 content → 保存 → version 自增
- [x] 6.4 点 "提交评审"(此时内容过短)→ 弹错"content 太短" / "缺少必要章节"
- [x] 6.5 补全章节(背景/目标/功能设计)+ 内容达 200 字 → 保存 → 提交 → 跳列表 → 状态变 "待评审"
- [x] 6.6 删除一份 DRAFT → 列表里消失
- [x] 6.7 点编辑页 "📎 上传文档" → 选一份 markdown → 成功 → 自动跳转编辑页 → 看到 AI 摘要回填

## 7. 归档准备

- [x] 7.1 把 spec 增量合并到 `openspec/specs/prd-storage/spec.md`(ADDED 两条 requirement 追加末尾)
- [x] 7.2 归档:`mv openspec/changes/add-prd-frontend openspec/changes/archive/$(date +%Y-%m-%d)-add-prd-frontend`
- [x] 7.3 在 `openspec/roadmap.md` 阶段二 #5 行下方追加 `5.1 | add-prd-frontend | PRD 列表/编辑器接通后端 | #5 | ✅ DONE | <日期>`
