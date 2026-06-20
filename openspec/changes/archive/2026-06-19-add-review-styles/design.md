## Context

DDD 四层架构已就绪，PRD（#5）、AI 基础设施（#4.5）、评审员管理（#8）、评审员试跑（#9）均已落地。评审风格是评审任务的"深度/态度"开关，与"评审员（角色视角）"正交，发起评审时按"评审员 × 风格"两个维度选择，由 #15 `add-prompt-composer` 将两者拼装到统一 Prompt 中。

前端原型 `frontend/app.html` 的"评审风格"页已有 4 个硬编码静态卡片（⚡ 宽松 / 🎯 务实 / 📋 标准（默认）/ 🔬 严谨），本 change 将其落地为后端 CRUD + 前端动态渲染。

错误码 60000 段已预留 `STYLE_NOT_FOUND(60003)`，可直接复用；本 change 新增 `STYLE_DEFAULT_NOT_DELETABLE(60004)`、`STYLE_RULE_INVALID(60005)`、`STYLE_DEFAULT_NOT_DISABLABLE(60006)`。

## Goals / Non-Goals

**Goals:**

- 落地 `ReviewStyle` 聚合根及完整 CRUD 生命周期
- 实现"默认风格唯一性"领域不变量，通过专用接口原子切换
- 默认风格保护：不可禁用、不可删除
- 规则列表使用 JSON 单列存储（整体读写，无单查需求）
- 提供 4 个种子风格，开箱可用且与前端原型完全对齐
- ADMIN 独占写操作，所有登录用户可读

**Non-Goals:**

- 评审风格与评审员的组合逻辑（#15 `add-prompt-composer`）
- 风格的运行时调用（#17/#18）
- 风格的标签/分组管理
- 规则字段的版本管理（评审风格整体可乐观锁更新即可）

## Decisions

### D1: 规则存储方案 — 单 TEXT 列 + JSON 字符串

**选择**：`rules` 字段为 TEXT 类型，存储 JSON 数组字符串（4–8 个对象，每个对象含 `label` 与 `content` 两字段）。Application 层使用 Jackson `ObjectMapper` 序列化/反序列化。

**理由**：
- 规则在业务上总是"整体读写、整体编辑、整体注入 Prompt"，没有单条查询需求
- 拆独立 `review_style_rule` 表会引入无谓的 JOIN 与维护复杂度
- 数量上限 8 条，单字段长度可控（TEXT 远超需求）
- Jackson 已在项目中作为默认 JSON 序列化器，无新依赖

**备选**：拆 `review_style_rule` 独立表 + 外键。优点是模型规范；缺点是 1:N 关系下每次保存需要 delete + insert，且业务上从不单独查询单条规则，得不偿失。

### D2: 默认风格唯一性不变量 — 应用层事务 + 专用接口

**选择**：
- 不通过部分唯一索引保证（MySQL 的部分唯一索引能力有限），而是在 Application 层用事务保证
- 新增专用接口 `POST /api/v1/review-styles/{id}/set-default`，原子地将目标风格置为 `isDefault=true` 并清空其他风格的 `isDefault` 标记
- 普通 PUT 更新接口禁止修改 `isDefault` 字段（请求体中忽略该字段），强制走专用接口
- 普通 POST 创建接口禁止设置 `isDefault=true`（必须先创建再 set-default）

**理由**：
- 集中化"默认风格"切换逻辑，避免多入口导致不变量被破坏
- 接口语义清晰（"设为默认"是动词，符合 RESTful action 风格）
- 创建/更新与"设为默认"职责分离，前端 UI 也更直观（编辑 Modal 不含 isDefault 字段，列表卡片有独立"设为默认"按钮）

**备选**：在 PUT 中允许传 `isDefault=true` 并由后端自动清理其他记录——优点是接口少；缺点是 PUT 的语义被污染（"更新"还是"切换默认"？），且容易被误用。

### D3: 默认风格保护 — 不可禁用、不可删除

**选择**：
- 删除接口若 `isDefault=true`，抛 `STYLE_DEFAULT_NOT_DELETABLE`
- 更新接口若试图把 `isDefault=true` 的风格 `enabled=false`，抛 `STYLE_DEFAULT_NOT_DISABLABLE`
- 系统永远恰好有 1 个 `isDefault=true && enabled=true` 的风格作为发起评审时的兜底

**理由**：
- 用户发起评审时"默认风格"是必选项，若被禁用/删除会破坏前端 UX
- 用 set-default 接口先切换默认风格、再禁用/删除原风格，是受控的迁移路径

### D4: 领域模型 — 单聚合根 ReviewStyle

**选择**：`ReviewStyle` 作为独立聚合根，rules 作为聚合根内的值对象集合（`List<StyleRule>`）。

**理由**：
- rules 与风格 1:1 强绑定，无独立生命周期
- StyleRule 是不可变值对象（record），由聚合根持有
- 规则数量校验（4–8 条）属于聚合根不变量，封装在工厂方法 + update 行为内

### D5: 规则数量约束 — 4 到 8 条

**选择**：规则数量必须在 4–8 条区间，违反时抛 `STYLE_RULE_INVALID`。

**理由**：
- 与前端原型完全一致：宽松 4 条、务实 5 条、标准 6 条、严谨 7 条
- 太少的规则不足以体现风格差异，太多则 Prompt 噪声大
- 后续 #15 拼装时直接遍历规则列表生成 Prompt 片段

### D6: 排序机制 — sortOrder 整数字段

**选择**：`sortOrder` 整数字段，默认 0，升序排列。前端按 `sortOrder ASC, id ASC` 渲染。

**理由**：
- 与评审员管理（#8）保持一致的排序约定
- 评审风格数量不多（通常 4–8 个），简单整数足够

### D7: 种子数据 — Flyway 迁移脚本

**选择**：`V5__create_review_style_table.sql` 末尾 INSERT 4 条种子数据，与前端原型 `app.html` 完全对齐。

**理由**：
- 与评审员管理（#8）的做法一致
- 开箱即用，避免 ADMIN 首次部署后还需要手动创建

### D8: 权限模型 — 复用 @RequireRole

**选择**：
- 写操作（POST/PUT/DELETE/set-default）：`@RequireRole(UserRole.ADMIN)`
- 读操作（GET 列表/详情）：`@RequireRole(UserRole.SUBMITTER)`（所有已登录用户）

**理由**：
- 评审风格是全局资源，所有用户在发起评审时需要看到可选风格列表
- 仅 ADMIN 可修改，避免普通用户误改规则配置

## Risks / Trade-offs

- **[默认风格切换非原子性风险]** → 若 set-default 事务回滚不及时可能出现"无默认"或"多默认"瞬态。Mitigation：set-default 操作放在 `@Transactional` 内，先批量 `UPDATE review_style SET is_default=0 WHERE is_default=1`，再 `UPDATE review_style SET is_default=1 WHERE id=?`，全程数据库事务保证一致性。
- **[JSON 规则字段无 schema 校验]** → 数据库层无法约束 `rules` 列内的 JSON 结构。Mitigation：所有写入路径必须经过 Application 层，由聚合根 `validateRules()` 强制校验数量与字段非空；不向外暴露绕过聚合根的写入入口。
- **[规则数量上限 8 条偏紧]** → 后续若需要更多规则需调整聚合根校验。Mitigation：数量上限是 domain 层常量，集中修改成本低。
- **[ErrorCode 40004 与 60003 重复]** → `ErrorCode.java` 同时存在 `REVIEW_STYLE_NOT_FOUND(40004)` 与 `STYLE_NOT_FOUND(60003)`。Mitigation：本 change 统一使用 60003，并在实现阶段提交将 40004 标注为 deprecated 或删除（不在本 change 范围内做迁移）。
