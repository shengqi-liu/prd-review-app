## Why

RAG 知识库的"上游链路"目前是空的——没有任何机制把企业内的业务知识（产品规范、合规规则、历史决策）拉进系统。后续 #12 索引器、#13 检索器都依赖一个"持续可信的文档源 + 变更通知"。本 change 落地这条上游链路的第一环：**监听本地 Git 仓库 + 定时拉取 + 变更检测 + 事件投递**。

为什么选 Git 作为知识源：
- 业务知识天然适合 markdown + 版本管理（产品规范、PRD 历史、规则手册）
- Git 仓库本身就提供变更历史、责任人、回滚能力，不需要再造一套
- 团队已有的 Wiki / Notion 都可以通过同步脚本落到 Git 仓库

为什么"检测+事件"和"索引"分两个 change：
- 单一职责：本 change 只回答"哪些 markdown 变了"，#12 回答"变了之后怎么向量化"
- 解耦：变更检测的实现（轮询 vs webhook）、索引存储（Chroma vs 其他）可以独立演进
- 可测试：本 change 用纯 git mock 就能验，无需起 Chroma；#12 测试不用真实 git 仓库

## What Changes

- **配置**：知识库 Git 仓库元数据（URL、本地 clone 路径、分支、轮询间隔、可选凭据），存数据库一行（支持 ADMIN 在管理页配置）
- **JGit 集成**：首次启动 → clone；后续启动 → fetch + reset to remote branch；支持 HTTPS（用户名+token）和 SSH（私钥路径）两种凭据
- **定时轮询**：`@Scheduled` 任务，间隔可配（默认 1 小时），每次 pull 后对比上次同步的 commit hash，提取新增 / 修改 / 删除的 `*.md` 文件
- **变更事件**：发布 Spring `ApplicationEvent`（`KbDocumentChangedEvent`），含 path、changeType（ADDED/MODIFIED/DELETED）、commit hash、仓库 id；#12 索引器作为监听者消费
- **元数据持久化**：`kb_repository` 表存仓库配置 + `last_synced_commit` + `last_synced_at` + `sync_status`（HEALTHY/SYNCING/ERROR）+ `last_error_message`
- **管理 API**：ADMIN 可配置/编辑/触发立即同步/查看状态，普通用户只读状态摘要（供前端"知识库"页"索引状态"卡片用）
- **错误码**：复用 50003 `KB_GIT_REPO_NOT_FOUND`；新增 `KB_GIT_CLONE_FAILED(50004)`、`KB_GIT_PULL_FAILED(50005)`、`KB_GIT_AUTH_FAILED(50006)`、`KB_REPO_ALREADY_CONFIGURED(50007)`

## Capabilities

### New Capabilities

- `kb-git-watcher`: Git 仓库元数据 CRUD、定时 pull、变更检测、变更事件投递、同步状态管理

### Modified Capabilities

（无）

## Impact

- **数据库**：新增 `kb_repository` 表（V6 迁移）
- **错误码**：新增 4 个错误码（50004–50007）
- **新依赖**：`org.eclipse.jgit:org.eclipse.jgit`、`org.eclipse.jgit:org.eclipse.jgit.ssh.apache`（SSH 凭据支持，可选）
- **配置**：`application.yml` 新增 `kb.git.*` 段（默认轮询间隔、clone 根目录、超时）
- **权限**：仓库 CRUD 仅 ADMIN，状态只读对所有登录用户
- **领域事件契约**：定义 `KbDocumentChangedEvent`，作为本 change 与 #12 的契约边界
- **测试**：纯单元测试用本地临时 git 仓库（JGit 内置 InitCommand），不依赖外部网络
- **前端**：本 change 只落地后端 API；"知识库"页的"重建索引"、"配置路径"按钮可在本 change 接通配置/触发同步两个动作，索引列表/检索测试留给 #12 / #14
