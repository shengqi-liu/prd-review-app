## Why

#5.1 (`add-prd-frontend`) 完成了 PRD CRUD 接通,但评审流程剩下 4 个原型页(选评审员/定评审风格/提交评审/评审中)还是写死假数据。用户走完"+ 新建方案"后看到的不是真实可操作的流程,而是积分体系示例 + AI 批注卡片。本 change 把这条流程从入口到提交闭环接通,让用户能真正"提交一份 PRD 进入待评审状态"。

## What Changes

### 三个流程页接通真后端
- **选评审员**:从 `GET /api/v1/reviewers?enabled=true` 拉真实数据,多选并 localStorage 持久化
- **定评审风格**:从 `GET /api/v1/review-styles` 拉真实数据,单选 + 默认 isDefault,localStorage 持久化
- **提交评审**:展示真实 PRD content(`GET /prds/{id}`)+ 已选评审员/风格摘要,点击触发真后端 `POST /prds/{id}/submit`

### 新增"评审详情"页(替代原型 #page-reviewing)
- 列表点击 SUBMITTED+ 状态卡片 → `#review?id=N` → 评审详情(只读 PRD 快照 + 已选评审员/风格 + 状态徽标 + 顶部进度栏 4 步全 ✓ done)
- 不再回编辑器/评审流程页

### URL hash 作为单一可信 PRD id 源(关键修复)
- 之前 `currentPrdId()` 依赖 `#prd-edit-id` hidden input,某些场景下值为空导致 localStorage 写到空 key
- 改:`currentPrdId()` 优先从 URL hash query 取 id(`#agent?id=N` / `#style?id=N` / `#precheck?id=N` / `#review?id=N`),fallback hidden input
- 所有"下一步"按钮跳转都用带 id 的 hash

### 流程锁定(已提交不可再改)
- 编辑器加载 SUBMITTED+ 状态 PRD:输入框 disabled,「保存」「删除」「下一步」按钮全隐藏
- 流程页 `guardFlowAccess`:已提交状态进 agent/style/precheck → 弹错 + 自动跳评审详情

### 进度记录(评审流程位置持久化)
- 走到第 N 步自动 `saveFlowProgress(prdId, step)` 写 localStorage `prd_flow_<id>`
- 列表 DRAFT/INITIALIZING 卡片显示「📍 第 N/4 步 · xxx」标签
- 已提交状态自动清"流程位置",保留"评审员/风格选择"(评审详情页要展示)

### 文案与视觉统一
- 进度栏改为 4 步:**编写方案 → 选评审员 → 定评审风格 → 提交评审**(删评审报告节点)
- 评审中状态徽标和 stat-card 文案统一为"评审中"(SUBMITTED + UNDER_REVIEW 合并)
- 侧边栏「我的方案」徽章动态化(同步 PRD 总数),删除原型「评审管理」入口

### 输入校验灵活化
- `RequiredSection` 同义词从精确匹配改 substring 匹配,扩展中英文关键词(背景/概述/痛点/Background...)
- "一、需求背景" / "项目背景" / "用户痛点" 等常见写法都能识别

## Capabilities

### Modified Capabilities

- `prd-storage`:前端流程页对接;`RequiredSection` 匹配从精确改 substring + 扩展同义词

## Impact

- **前端**:`frontend/app.html`(进度栏 + 流程页 + 评审详情 + 编辑器锁定)、`frontend/js/prd.js`(URL 路由 + 列表渲染 + 编辑器状态控制)、`frontend/js/prd-flow.js`(新增,流程页数据加载与选择)
- **后端**:`RequiredSection.matches` substring 匹配 + 同义词扩展(`domain/.../prd/validation/RequiredSection.java`)
- **测试**:`PrdInputValidatorTest` 现有 18 个用例全过(substring 匹配不破坏既有断言)
- **无 DB schema / API 契约变更**

## Out of Scope

- 不接通真实评审运行(多 Agent 并行 + 结果聚合 = #17/#18 范围)
- 不持久化评审员/风格选择到后端(localStorage 即可,后续 #17 接通后改后端字段)
- 不做评审报告展示(原 `#page-report` 保留为原型,#20/#21 落地后再接)
- 不做评审管理表格(`#page-review-manage` 入口已删,内容保留作未来 #21 报告管理基础)
