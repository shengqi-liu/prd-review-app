## Why

评审风格是评审任务的"深度/态度"开关，与"评审员（角色视角）"正交。用户发起评审时按"评审员 × 风格"两个维度选择，#15 `add-prompt-composer` 把它俩拼装到一份 Prompt 里。前端原型 `app.html` 的"评审风格"页已设计好 4 个种子风格（⚡ 宽松 / 🎯 务实 / 📋 标准（默认）/ 🔬 严谨），目前是硬编码静态卡片，需要落地后端 CRUD + 前端动态渲染。

风格的存在解决一个具体问题：**同一份 PRD、同一位评审员，根据项目阶段不同（小迭代 vs 大版本发版），ADMIN 希望评审输出的深度、严苛程度、字段细节都不同**。把这种调整从 Prompt 里拎出来作为独立配置，比每次评审都改评审员 Prompt 干净得多。

## What Changes

- 新增 `ReviewStyle` 聚合根（domain 层），含名称、emoji 图标、适用场景描述、规则列表（4–8 个 label-content 对）、启用状态、默认标志、排序权重等字段
- 规则用 JSON 存储（一个 TEXT 列），不拆独立表——因为业务上规则总是整体读写、整体编辑、整体注入到 Prompt，没有单独查询的需求
- **默认风格唯一性不变量**：系统永远恰好有 1 个 `isDefault=true` 的启用风格；通过专用接口 `POST /api/v1/review-styles/{id}/set-default` 原子切换
- 默认风格不可禁用、不可删除（保护用户发起评审时的"默认选项"始终存在）
- 数据库迁移脚本 `V5__create_review_style_table.sql` + 4 个种子风格 INSERT（与原型完全一致）
- REST API `/api/v1/review-styles`：CRUD + 设为默认；ADMIN 写、所有登录用户读
- 前端 `frontend/app.html` 的"评审风格"页改为动态渲染 + 编辑/启停/设为默认/删除 Modal

## Capabilities

### New Capabilities

- `review-styles`: 评审风格领域模型、CRUD 接口、默认风格唯一性约束、前端管理页

### Modified Capabilities

（无）

## Impact

- **数据库**：新增 `review_style` 表（Flyway V5）+ 4 条种子数据
- **错误码**：`STYLE_NOT_FOUND(60003)` 已预留；本 change 新增 `STYLE_DEFAULT_NOT_DELETABLE(60004)`、`STYLE_RULE_INVALID(60005)`、`STYLE_DEFAULT_NOT_DISABLABLE(60006)`
- **权限**：复用 `@RequireRole` 注解；写操作 ADMIN only
- **新依赖**：无（JSON 序列化复用现有 Jackson `ObjectMapper`）
- **API**：5 个 RESTful 端点 + 1 个领域操作端点（set-default）
- **前端**：原型"评审风格"页从硬编码改为后端 API 驱动
- **测试**：领域单元测试（聚合根不变量）+ 应用服务测试（默认风格切换原子性）+ 全套接口测试
