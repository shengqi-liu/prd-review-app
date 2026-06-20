## 1. 数据库迁移

- [x] 1.1 创建 `db/migration/V4__create_reviewer_table.sql`：建 `reviewer` 表（id BIGINT AUTO_INCREMENT, name VARCHAR(100) NOT NULL, icon VARCHAR(20), description VARCHAR(500), prompt_template TEXT NOT NULL, enabled TINYINT DEFAULT 1, sort_order INT DEFAULT 0, version INT DEFAULT 1, deleted TINYINT DEFAULT 0, created_at DATETIME, updated_at DATETIME, PRIMARY KEY(id)）；添加唯一索引 `uk_reviewer_name`（name, deleted）用于未删除记录的名称唯一性约束
- [x] 1.2 在同一脚本末尾 INSERT 5 条种子评审员（与前端原型一致：产品顾问 🧑‍💼 sortOrder=10 enabled=1、技术架构师 🏗️ sortOrder=20 enabled=1、商业分析师 📊 sortOrder=30 enabled=1、竞品研究员 🔍 sortOrder=40 enabled=1、合规风控官 🛡️ sortOrder=50 enabled=0），每条含完整 Prompt 模板（至少包含 `{{prd_title}}` 和 `{{prd_content}}` 占位符）

## 2. Domain 层

- [x] 2.1 创建 `domain/.../reviewer/model/Reviewer.java` 聚合根（纯 Java 对象）：字段 id、name、icon、description、promptTemplate、enabled、sortOrder、version、deleted、createdAt、updatedAt；静态工厂 `create(name, icon, description, promptTemplate)` → enabled=true, sortOrder=0, version=1；`reconstruct(...)` 用于持久化重建
- [x] 2.2 在 `Reviewer` 中实现 Prompt 模板校验领域行为 `validatePromptTemplate()`：正则 `\{\{(\w+)\}\}` 提取所有变量名，与白名单常量集合 `ALLOWED_PLACEHOLDERS`（prd_title, prd_content, review_style, kb_context）比对，非法变量抛 `BizException(REVIEWER_PROMPT_INVALID)`，错误信息包含非法变量名列表
- [x] 2.3 在 `Reviewer` 中实现 `update(name, icon, description, promptTemplate, enabled, sortOrder)` 方法，内部调用 `validatePromptTemplate()`
- [x] 2.4 在 `Reviewer` 中实现 `markDeleted()` 逻辑删除方法
- [x] 2.5 创建 `domain/.../reviewer/repository/ReviewerRepository.java` 接口（findById、save、update、softDelete、findPageByCondition、existsByName）

## 3. Infrastructure 层

- [x] 3.1 创建 `infrastructure/.../reviewer/po/ReviewerPO.java`（`@TableName("reviewer")`、`@Version`、`@TableLogic`，字段与表结构对应，含 icon）
- [x] 3.2 创建 `infrastructure/.../reviewer/mapper/ReviewerMapper.java`（继承 `BaseMapper<ReviewerPO>`）
- [x] 3.3 创建 `infrastructure/.../reviewer/assembler/ReviewerAssembler.java`（ReviewerPO ↔ Reviewer 双向转换）
- [x] 3.4 实现 `infrastructure/.../reviewer/repository/ReviewerRepositoryImpl.java`：`findPageByCondition` 根据 enabled 参数和角色组装 `LambdaQueryWrapper`，排序 `sortOrder ASC, id ASC`；`existsByName(name, excludeId)` 检查名称唯一性（排除自身 id 和已删除记录）

## 4. Application 层

- [x] 4.1 创建 Command 对象：`CreateReviewerCommand`（name、icon、description、promptTemplate）、`UpdateReviewerCommand`（name、icon、description、promptTemplate、enabled、sortOrder、version）、`ReviewerQueryCommand`（page、size、enabled、currentUserRole）
- [x] 4.2 创建 `ReviewerDTO` record（id、name、icon、description、promptTemplate、enabled、sortOrder、version、createdAt、updatedAt）和 `ReviewerPageResult`（total、items）
- [x] 4.3 实现 `ReviewerApplicationService.create()`：校验 name 非空、调用 `Reviewer.create()` + `validatePromptTemplate()`、检查名称唯一性（existsByName）、Repository 保存、返回 ReviewerDTO
- [x] 4.4 实现 `ReviewerApplicationService.update()`：查找 Reviewer、检查名称唯一性（排除自身）、调用 `reviewer.update()`、Repository 更新、返回 ReviewerDTO
- [x] 4.5 实现 `ReviewerApplicationService.delete()`：查找 Reviewer、执行逻辑删除
- [x] 4.6 实现 `ReviewerApplicationService.getById()`：查找 Reviewer、不存在则抛 `REVIEWER_NOT_FOUND`、返回 ReviewerDTO
- [x] 4.7 实现 `ReviewerApplicationService.listReviewers()`：按角色决定是否强制 enabled=true（非 ADMIN 强制），分页查询，返回 ReviewerPageResult

## 5. API 层

- [x] 5.1 创建请求 DTO：`CreateReviewerRequest`（@NotBlank name、icon、description、@NotBlank promptTemplate）、`UpdateReviewerRequest`（@NotBlank name、icon、description、@NotBlank promptTemplate、@NotNull enabled、sortOrder、@NotNull version）
- [x] 5.2 创建响应 DTO：`ReviewerResponse`（id、name、icon、description、promptTemplate、enabled、sortOrder、version、createdAt、updatedAt）
- [x] 5.3 实现 `ReviewerController`：`POST /api/v1/reviewers`（@RequireRole ADMIN）创建评审员
- [x] 5.4 实现 `ReviewerController`：`PUT /api/v1/reviewers/{id}`（@RequireRole ADMIN）更新评审员
- [x] 5.5 实现 `ReviewerController`：`DELETE /api/v1/reviewers/{id}`（@RequireRole ADMIN）逻辑删除
- [x] 5.6 实现 `ReviewerController`：`GET /api/v1/reviewers/{id}`（@RequireRole SUBMITTER）查看详情
- [x] 5.7 实现 `ReviewerController`：`GET /api/v1/reviewers`（@RequireRole SUBMITTER）分页列表，含可选 enabled 筛选参数

## 6. 异常处理

- [x] 6.1 在 `GlobalExceptionHandler` 中确认 `DATA_CONFLICT` 错误码已被正确处理（名称唯一性冲突复用 `ErrorCode.DATA_CONFLICT`）

## 7. 测试

- [x] 7.1 `ReviewerTest`（纯领域单元测试）：create 工厂方法默认值、validatePromptTemplate 合法/非法/纯文本/空模板、update 方法触发校验、markDeleted 设置 deleted 标记
- [x] 7.2 `ReviewerApplicationServiceTest`：create 成功 + 名称重复冲突 + 模板非法
- [x] 7.3 `ReviewerApplicationServiceTest`：update 成功 + 乐观锁冲突 + 名称冲突（排除自身）
- [x] 7.4 `ReviewerApplicationServiceTest`：delete 成功 + 不存在时抛异常
- [x] 7.5 `ReviewerApplicationServiceTest`：getById 存在/不存在
- [x] 7.6 `ReviewerApplicationServiceTest`：listReviewers ADMIN 看全部 / SUBMITTER 仅看 enabled=true / 排序正确

## 8. 前端联调（基于 frontend/app.html 原型）

- [x] 8.1 创建 `frontend/js/reviewer.js`：封装 `loadReviewers()`、`openCreateModal()`、`openEditModal()`、`submitReviewerModal()`、`toggleReviewer()`、`deleteReviewer()`、`insertPlaceholder()`，统一通过 `authFetch` 调用 `/api/v1/reviewers`
- [x] 8.2 修改 `frontend/app.html`：删除"AI 评审员"页面的硬编码静态卡片，替换为单一 `.reviewer-list` 容器（由 JS 动态渲染）；保留 ＋ 新建评审员 按钮并绑定 `onclick="openCreateModal()"`
- [x] 8.3 在 `frontend/app.html` 末尾增加新建/编辑评审员 Modal（含 name/icon/description/promptTemplate/enabled/sortOrder 输入项 + 4 个占位符快捷插入按钮）；同步增加对应 Modal CSS
- [x] 8.4 在 `navTo()` 函数末尾加入 `if (name === 'reviewer-manage') loadReviewers()` 钩子，切换到该页时自动加载列表
- [x] 8.5 在 `app.html` 引入 `<script src="js/reviewer.js"></script>`（位于 auth.js 之后）
- [x] 8.6 角色差异渲染：ADMIN 看到所有评审员 + 写操作按钮；非 ADMIN 仅渲染只读卡片（编辑/删除/启停按钮不出现）；"🧪 测试"按钮在本 change 中显示为 disabled（待 #9 实现）
