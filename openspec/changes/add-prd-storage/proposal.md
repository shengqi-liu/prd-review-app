## Why

系统目前已具备用户认证、RBAC 权限和 AI 基础设施，但缺少核心业务载体——PRD 文档的存储与管理能力。没有 PRD 实体，后续的 AI 评审、RAG 检索、报告生成等所有阶段均无法推进。

## What Changes

- 新增 `Prd` 聚合根（domain 层），包含完整状态机（INITIALIZING → DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED）
- 新增 `PrdVersion` 版本快照实体，在每次提交评审时自动拍取
- 新增 `PrdRepository` 接口（domain）及 MyBatis-Plus 实现（infrastructure）
- 新增 `PrdApplicationService`（application 层），编排用例：手动创建、URL 路径创建（SSE）、更新、软删除、提交评审
- 新增 REST API（api 层）：`POST /api/v1/prds`、`GET /api/v1/prds`、`GET /api/v1/prds/{id}`、`PUT /api/v1/prds/{id}`、`DELETE /api/v1/prds/{id}`、`POST /api/v1/prds/{id}/submit`
- 新增数据库迁移脚本 `V3__create_prd_tables.sql`（`prd` 表 + `prd_version` 表）
- 新增前端 PRD 列表页（`prd-list.html`）和创建/编辑页（`prd-edit.html`）
- 新增错误码：`PRD_NOT_FOUND(30001)`、`PRD_ACCESS_DENIED(30002)`、`PRD_VERSION_CONFLICT(30004)`

## Capabilities

### New Capabilities
- `prd-storage`: PRD 聚合根、状态机、版本快照、CRUD + 提交评审 REST API、前端列表与编辑页

### Modified Capabilities
- `auth`: 无需求级别变更（鉴权逻辑复用现有 JWT，无新增要求）

## Impact

- **domain 模块**：新增 `com.prdreview.prd` 包（聚合根、枚举、Repository 接口）
- **application 模块**：新增 `com.prdreview.prd` 包（ApplicationService）
- **infrastructure 模块**：新增 `com.prdreview.prd` 包（Mapper、Repository 实现）
- **api 模块**：新增 `com.prdreview.prd` 包（Controller、Request/Response DTO）
- **db/migration**：新增 `V3__create_prd_tables.sql`
- **frontend**：新增 `prd-list.html`、`prd-edit.html`
- **依赖**：无新增 Maven 依赖（MyBatis-Plus、Spring AI 已在 #4.5 引入）
