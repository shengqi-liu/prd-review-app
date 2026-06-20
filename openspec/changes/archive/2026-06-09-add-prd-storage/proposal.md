## Why

PRD 是系统的核心数据实体——用户提交 PRD 才能触发 AI 评审流程。当前系统已具备登录鉴权和 RBAC，但没有任何 PRD 存储与管理能力，无法进入 M3 里程碑（PRD 可提交）。

## What Changes

- **新增** `prd` 聚合根（domain 层）：包含 title、content、authorId、status、version（乐观锁）、createdAt、updatedAt
- **新增** `PrdStatus` 枚举：DRAFT / SUBMITTED / UNDER_REVIEW / APPROVED / REJECTED
- **新增** `prd_version` 快照实体：每次 DRAFT→SUBMITTED 时自动拍一份版本快照
- **新增** `PrdRepository` 接口（domain）+ `PrdRepositoryImpl`（infrastructure，MyBatis-Plus）
- **新增** `PrdVersionRepository` 接口 + 实现
- **新增** `PrdApplicationService`：编排 CRUD + submit 用例
- **新增** 6 个 REST 接口（api 层）：创建草稿、详情、分页列表、更新草稿、软删除、提交评审
- **新增** Flyway 迁移脚本 `V3__create_prd_tables.sql`

## Capabilities

### New Capabilities

- `prd-storage`: PRD 实体 CRUD、版本快照、状态机（DRAFT→SUBMITTED）、分页查询、软删除

### Modified Capabilities

（无，现有 auth/rbac spec 不变）

## Impact

- **数据库**：新增 `prd` 表、`prd_version` 表（Flyway V3）
- **新增模块文件**：domain / application / infrastructure / api 各层均有新增
- **依赖**：MyBatis-Plus（已在 pom）、Flyway（已在 pom）
- **权限**：使用 `@RequireRole` + `CurrentUser`（change#4 已实现）
- **下游影响**：change#6（输入门槛校验）、change#7（文档解析）均依赖本 change
