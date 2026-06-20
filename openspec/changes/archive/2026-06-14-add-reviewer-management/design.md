## Context

系统已有完整的 DDD 四层架构（domain / application / infrastructure / api），PRD 域（#5）和 AI 基础设施（#4.5）已落地。评审员是评审流水线的核心配置单元——每位评审员代表一种评审视角（产品、技术、UX 等），通过 Prompt 模板 + 角色描述告诉 AI 以何种身份对 PRD 进行评审。

当前错误码 60000 段已预留 `REVIEWER_NOT_FOUND(60001)` 和 `REVIEWER_PROMPT_INVALID(60002)`，可直接复用。

## Goals / Non-Goals

**Goals:**

- 落地 `Reviewer` 聚合根及完整 CRUD 生命周期
- 实现 Prompt 模板占位符校验机制，确保模板引用的变量都是系统已定义的
- 提供种子评审员，开箱可用
- ADMIN 独占写操作，TEAM_MEMBER / SUBMITTER 可读（列表+详情）

**Non-Goals:**

- Prompt 试跑/预览（#9 `add-reviewer-test-endpoint`）
- 评审员与评审风格的组合逻辑（#15 `add-prompt-composer`）
- 评审员的运行时调用与 Agent 编排（#17/#18）
- 评审员分组或标签管理

## Decisions

### D1: Prompt 模板占位符方案 — Mustache 风格双花括号 + 关注点分层

**选择**：`{{variable_name}}` 语法，纯正则校验，白名单仅 `{prd_title, prd_content}`。

**理由**：
- 评审员模板的职责边界：**只描述"角色 + 如何评审 PRD 内容"**，因此只需要 PRD 级别的占位符
- `review_style`（评审风格）、`kb_context`（RAG 检索结果）属于**运行时上下文数据**，由 Prompt Composer（#15）在拼装阶段通过外层包装注入；这两类内容是评审任务级别的开关，与"哪位评审员"正交，强行塞进评审员模板会模糊职责
- 纯正则 `\{\{(\w+)\}\}` 提取 → 与白名单集合比对，实现简单、无依赖
- 后续 #15 Prompt 拼装时直接 `String.replace("{{prd_title}}", title).replace("{{prd_content}}", content)`，外层再拼接风格/RAG 段

**备选**：把 4 个占位符全部放进白名单——优点是 ADMIN 控制力强，缺点是把"角色定义"和"运行时拼装"两个关注点混在一起，违反单一职责。

### D2: 领域模型设计 — 单聚合根 Reviewer

**选择**：`Reviewer` 作为独立聚合根，不拆分 PromptTemplate 为独立实体。

**理由**：
- Prompt 模板是评审员的核心属性，1:1 绑定，无独立生命周期
- 模板校验是评审员的领域行为，放在聚合根内聚合
- 后续若需模板版本管理，再抽取为值对象/子实体

### D3: 启用/禁用机制 — enabled 布尔字段

**选择**：`enabled` 布尔字段，默认 `true`。禁用的评审员不出现在评审选择列表，但 ADMIN 可在管理界面看到。

**理由**：
- 比 status 枚举（ACTIVE/INACTIVE/ARCHIVED）更简单，当前只有"可用/不可用"两态需求
- CRUD 删除为逻辑删除（`deleted`），与启用/禁用是正交维度

### D4: 排序机制 — sortOrder 整数字段

**选择**：`sortOrder` 整数字段，默认 0，升序排列。ADMIN 可手动调整展示顺序。

**理由**：
- 评审员数量不会很多（通常 3-10 个），简单整数排序足够
- 前端展示时按 `sortOrder ASC, id ASC` 排列

### D5: 种子数据 — Flyway 迁移脚本

**选择**：在建表的同一个 Flyway 脚本末尾 INSERT 种子数据。

**理由**：
- 保证新部署环境开箱即有默认评审员
- 种子数据与表结构强绑定，放同一脚本可维护性好

### D6: 权限模型 — 复用现有 @RequireRole

**选择**：
- 写操作（POST/PUT/DELETE）：`@RequireRole(UserRole.ADMIN)`
- 读操作（GET 列表/详情）：`@RequireRole(UserRole.SUBMITTER)`（即所有已登录用户）

**理由**：
- 评审员配置是全局资源，所有用户在发起评审时需要看到可选评审员列表
- 仅 ADMIN 可修改，避免普通用户误改 Prompt 模板

## Risks / Trade-offs

- **[Prompt 模板向前兼容]** → 白名单占位符变量集合需随后续 change 同步扩展（#10 可能增加 `review_style` 相关变量）。Mitigation：白名单定义为 domain 层常量集合，修改集中。
- **[种子数据 Prompt 质量]** → 预置的 Prompt 模板只是初始版本，实际使用中 ADMIN 会持续调优。Mitigation：这是预期行为，种子数据仅提供起点。
- **[无模板预览]** → 本 change 不含 Prompt 试跑能力，ADMIN 保存后无法立即验证效果。Mitigation：#9 `add-reviewer-test-endpoint` 紧随其后实现。
