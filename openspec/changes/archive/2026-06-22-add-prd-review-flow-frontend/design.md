## Context

#5.1 接通了列表和编辑器,但流程的中后段(选评审员/定风格/提交评审 + 提交后查看)都还是原型。用户体验是"前半段真后端,后半段假数据",无法形成完整闭环。本 change 把后半段全部接真。

## Goals / Non-Goals

**Goals:**
- 用户能从新建 PRD 一路点到"提交评审"成功,中间所有选择(评审员/风格)都被持久化
- 已提交 PRD 不能再被改动,但能查看完整快照(PRD 内容 + 评审员 + 风格)
- URL hash 作为单一可信状态源,刷新/前进后退/直接输入 URL 都能正确恢复
- 文案/视觉统一(进度栏 4 步 / 状态徽标 / 侧边栏徽章 / 章节匹配)

**Non-Goals:**
- 不接通真实 AI 评审运行(交给 #17/#18)
- 不持久化评审员/风格选择到后端表(localStorage 够用,后续真接通时再迁数据)
- 不重做"评审报告"页(交给 #20/#21)

## Decisions

### D1:评审员/风格选择用 localStorage,不上后端表
- 工程上最快;后端 PRD 表/PrdConfig 表都不需要改
- 局限:跨设备/跨浏览器看不到选择 → 评审详情页明示"本浏览器无记录"
- #17 真接通评审运行时,会把 agentIds + styleId 作为参数发给后端 submit endpoint,届时再考虑落库

### D2:URL hash 作为单一可信 PRD id 源
**之前的 bug 根因**:`currentPrdId()` 从 `#prd-edit-id` hidden input 读,但 input value 在某些跳转/刷新场景为空,导致 `localStorage.setItem('prd_agents_null', ...)` 写到废 key。

**修复**:所有流程页 hash 都带 id —— `#agent?id=N` / `#style?id=N` / `#precheck?id=N` / `#review?id=N`。`currentPrdId()` 优先解析 URL hash query,fallback hidden input。`handleHashRoute` 解析 URL id 同步写入 hidden input,作为流程切换的兜底。

### D3:评审详情用全新 `#review` 路由,不复用 `#edit`
- 编辑器和评审详情视图差异大(可编辑 vs 只读 + 配置摘要)
- 不复用避免编辑器代码被"非 DRAFT 时切换视图"逻辑污染
- 路由清晰:`#edit?id=N` = 编辑器,`#review?id=N` = 评审详情

### D4:进度栏 reviewing 状态显示"4 步全 done"
- isFlow 数组加入 'reviewing',`updateSteps` 对 reviewing/report 视为 idx=steps.length(全 done)
- 用户看到评审详情页时,顶部进度栏 4 个绿色 ✓,清晰反馈"流程已走完"

### D5:列表卡片"流程位置标签"只对未提交状态显示
- DRAFT/INITIALIZING:显示 `📍 第 N/4 步 · xxx`(localStorage `prd_flow_<id>`)
- 已提交状态:不显示,且渲染时清掉 `prd_flow_<id>` 残留(避免下次切回又出现)
- 保留 `prd_agents_<id>` / `prd_style_<id>`(评审详情要用)

### D6:RequiredSection substring 匹配 + 扩展同义词
- 之前精确匹配 "背景"/"目标"/"功能设计" 等死板,用户写"一、需求背景"识别不到
- 改 substring + 扩 3 倍同义词,常见中英文 PRD 章节名都能命中
- 阈值不变(至少 2 个核心章节),避免"灌水即可通过"

## Risks / Trade-offs

- **[localStorage 跨浏览器不同步]** → 评审详情可能看不到当初的评审员选择。Mitigation:页面明示"本浏览器无记录";#17 接通时改后端字段
- **[substring 匹配可能误判]** → 比如标题"积分体系背景调研" 会同时含"背景"和"实现"关键词,被算成 2 个章节。Mitigation:EnumSet 自动去重(同标题命中同类别只算 1 次);误判方向是"宽松通过",可接受
- **[URL 直接输入 hash 绕过 guardFlowAccess]** → guardFlowAccess 依赖 `currentEditPrd`,如果用户直输 `#agent?id=N` 没经过编辑器加载,`currentEditPrd=null` 会放行。可接受 — 流程页本身的 toggleFlowAgent/selectFlowStyle 写 localStorage 无害,真正的保护在提交时的后端状态机校验

## Migration Plan

无 DB 变更。前端文件刷新即生效。
