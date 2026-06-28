## 1. 前端流程页接通后端

- [x] 1.1 新建 `frontend/js/prd-flow.js` — 负责评审员/风格/确认提交三个页面的数据加载与选择持久化
- [x] 1.2 选评审员页:`loadFlowAgents` 拉 `/api/v1/reviewers?enabled=true`,`toggleFlowAgent` 多选写 localStorage
- [x] 1.3 定评审风格页:`loadFlowStyles` 拉 `/api/v1/review-styles`,`selectFlowStyle` 单选写 localStorage;首次默认选 isDefault
- [x] 1.4 提交评审页:`loadFlowPrecheck` 拉真实 PRD + localStorage 已选,渲染确认面板
- [x] 1.5 在 `navTo` 进 agent/style/precheck 时调 `maybeLoadFlowPage` 触发加载
- [x] 1.6 引入 `js/prd-flow.js` 到 `app.html`

## 2. 评审详情页(替代原型 #page-reviewing)

- [x] 2.1 改造 `#page-reviewing` HTML:动态容器(PRD 内容 + 配置面板 + 状态徽标 + 返回按钮)
- [x] 2.2 `prd.js` 新增 `loadReviewDetail(id)` — fetch PRD + 从 localStorage 取评审员/风格 + 渲染
- [x] 2.3 `handleHashRoute` 加 `#review?id=N` 分支
- [x] 2.4 `bcMap` 添加 `reviewing: '我的方案 › 评审详情'`

## 3. URL hash 作为单一可信 PRD id 源

- [x] 3.1 `currentPrdId()` 改为优先从 URL hash query 取 id,fallback hidden input
- [x] 3.2 `goNextFromEdit` / `goStepStyle` / `goStepPrecheck` 用带 id 的 hash 跳转
- [x] 3.3 `handleHashRoute` 对所有流程页统一把 URL id 同步进 hidden input(双向兜底)
- [x] 3.4 `handleHashRoute` 加 agent/style/precheck 三个分支
- [x] 3.5 暴露 `window.handleHashRoute` 供 prd-flow.js 跨文件调用

## 4. 已提交 PRD 流程锁定

- [x] 4.1 `openPrdEditor` 加载非 DRAFT PRD:输入框 disabled,「下一步」「保存」「删除」按钮全隐藏
- [x] 4.2 列表点击行为分流:`openPrdById` 先 fetch 状态,DRAFT 进 `#edit`,其他进 `#review`
- [x] 4.3 `prd-flow.js` 新增 `guardFlowAccess`:进 agent/style/precheck 前检查 PRD 状态,非 DRAFT 弹错跳 #review
- [x] 4.4 `getCurrentEditPrd` expose 到 window,供 guardFlowAccess 跨文件使用

## 5. 进度记录与列表展示

- [x] 5.1 `prd.js` 新增 `saveFlowProgress` / `loadFlowProgress` / `clearFlowProgress` / `describeFlowProgress`
- [x] 5.2 `navTo` 进流程页时自动调 saveFlowProgress(localStorage `prd_flow_<id>`)
- [x] 5.3 `renderPrdList` 对 DRAFT/INITIALIZING 状态显示「📍 第 N/4 步 · xxx」标签
- [x] 5.4 已提交状态自动清 `prd_flow_<id>`,保留 `prd_agents_/prd_style_`(评审详情要用)
- [x] 5.5 `submitForReview` 成功后只清"流程位置"标签,不清评审员/风格选择

## 6. 文案与视觉统一

- [x] 6.1 进度栏改 4 步:**编写方案 → 选评审员 → 定评审风格 → 提交评审**(删评审报告节点)
- [x] 6.2 `updateSteps` 数组同步删除 'report',reviewing 视为 idx=steps.length(4 步全 done)
- [x] 6.3 `STATUS_BADGE`:SUBMITTED 与 UNDER_REVIEW 统一为"评审中",APPROVED 改"已通过"
- [x] 6.4 stat-card 重排:全部 / 草稿 / 评审中 / 已通过 / 不通过(有数据才显示)
- [x] 6.5 侧边栏「我的方案」徽章动态化,绑定 `loadPrds` 时刷新
- [x] 6.6 删除侧边栏「评审管理」入口(原型未接通,留着误导)
- [x] 6.7 评审风格页 `goNextFromEdit` 按钮 + 评审风格页 / 评审员页 / 提交评审页 section-title 文案统一
- [x] 6.8 面包屑映射(`bcMap`)对齐新文案
- [x] 6.9 step id 从 `s-xxx` 改为 `step-xxx` 让 `updateSteps` 真正生效(修历史 bug)

## 7. 输入校验灵活化

- [x] 7.1 `RequiredSection.matches` 从精确匹配改 substring 包含匹配(不区分大小写)
- [x] 7.2 每个 RequiredSection 同义词扩展为 12-15 个中英文关键词
- [x] 7.3 验证 `PrdInputValidatorTest` 18 个用例不回归

## 8. 编辑器入口扩展

- [x] 8.1 编辑页右上角新增「🔗 从 URL 导入」按钮(已在前几轮加)
- [x] 8.2 新建 `frontend/js/prd-from-url.js` 处理 SSE 流式 URL 创建 PRD
- [x] 8.3 上传文档(`prd-upload.js`)成功后跳 `#edit?id=N` 而不是 alert + 跳列表

## 9. 归档准备

- [x] 9.1 spec 增量已合并到 `openspec/specs/prd-storage/spec.md`(在 #5/#6/#7 同一文件追加新 requirement)
- [x] 9.2 归档:本 change 创建在 `openspec/changes/archive/2026-06-22-add-prd-review-flow-frontend/`
- [x] 9.3 更新 `openspec/roadmap.md` 阶段二追加 5.2 行
