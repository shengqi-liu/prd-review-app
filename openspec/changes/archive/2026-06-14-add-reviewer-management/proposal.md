## Why

评审系统的核心是「AI 评审员」——每位评审员封装一套 Prompt 模板和角色定位，决定评审的视角与深度。当前系统已具备用户鉴权（#3/#4）、AI 基础设施（#4.5）和 PRD 存储（#5），但尚无评审员实体。后续的 Prompt 拼装（#15）、内审（#17）、正式评审（#18）全部依赖评审员配置，必须先行落地。

## What Changes

- 新增 `Reviewer` 聚合根（domain 层），含名称、角色描述、Prompt 模板、启用状态、排序权重等字段
- 新增评审员 CRUD API（`/api/v1/reviewers`），仅 ADMIN 可创建/编辑/删除，TEAM_MEMBER 及以上可列表查看
- Prompt 模板支持占位符变量（`{{prd_title}}`、`{{prd_content}}`、`{{review_style}}`、`{{kb_context}}`），系统在保存时校验模板语法合法性
- 内置种子数据：预置 3 位默认评审员（产品视角、技术视角、用户体验视角），通过 Flyway 迁移脚本插入
- 新增数据库表 `reviewer`，含逻辑删除和乐观锁

## Capabilities

### New Capabilities
- `reviewer-management`: AI 评审员的领域模型、CRUD 接口、Prompt 模板校验、种子数据

### Modified Capabilities

（无已有 spec 变更）

## Impact

- **数据库**：新增 `reviewer` 表（Flyway V4 迁移脚本）
- **错误码**：使用已预留的 60000 段（`REVIEWER_NOT_FOUND`、`REVIEWER_PROMPT_INVALID`）
- **权限**：复用现有 `@RequireRole` 注解做接口权限控制
- **依赖**：无新外部依赖，Prompt 模板占位符校验用纯 Java 正则实现
- **API**：新增 RESTful 端点 `POST/GET/PUT/DELETE /api/v1/reviewers`
