## 0. 前置：错误码确认

- [x] 0.1 在 `ErrorCode` 枚举 60000 段确认 `STYLE_NOT_FOUND(60003)` 已存在；新增 `STYLE_DEFAULT_NOT_DELETABLE(60004, "默认风格不可删除")`、`STYLE_RULE_INVALID(60005, "评审风格规则配置非法")`、`STYLE_DEFAULT_NOT_DISABLABLE(60006, "默认风格不可禁用")`；运行 `ErrorCodeTest` 验证唯一性

## 1. 数据库迁移

- [x] 1.1 创建 `db/migration/V5__create_review_style_table.sql`：建 `review_style` 表（id BIGINT AUTO_INCREMENT, name VARCHAR(50) NOT NULL, icon VARCHAR(20), scenario VARCHAR(200), rules TEXT NOT NULL, enabled TINYINT DEFAULT 1, is_default TINYINT DEFAULT 0, sort_order INT DEFAULT 0, version INT DEFAULT 1, deleted TINYINT DEFAULT 0, created_at DATETIME, updated_at DATETIME, PRIMARY KEY(id)）；添加唯一索引 `uk_review_style_name`（name, deleted）保证未删除记录的名称唯一性
- [x] 1.2 在同一脚本末尾 INSERT 4 条种子风格（与前端原型一致）：宽松 ⚡ sortOrder=10 isDefault=0 rules=4 条；务实 🎯 sortOrder=20 isDefault=0 rules=5 条；标准 📋 sortOrder=30 **isDefault=1** rules=6 条；严谨 🔬 sortOrder=40 isDefault=0 rules=7 条；rules 字段以 JSON 数组字符串存储

## 2. Domain 层

- [x] 2.1 创建 `domain/.../reviewer/style/model/StyleRule.java` 不可变 record（`String label`、`String content`）
- [x] 2.2 创建 `domain/.../reviewer/style/model/ReviewStyle.java` 聚合根（纯 Java 对象）：字段 id、name、icon、scenario、rules（List<StyleRule>）、enabled、isDefault、sortOrder、version、deleted、createdAt、updatedAt；静态工厂 `create(name, icon, scenario, rules, sortOrder)` → enabled=true、isDefault=false、version=1；`reconstruct(...)` 用于持久化重建
- [x] 2.3 在 `ReviewStyle` 中实现 `validateRules()` 领域方法：rules 数量必须在 4–8 条之间（含两端），每条 label 和 content 非空，违反抛 `BizException(STYLE_RULE_INVALID)`
- [x] 2.4 在 `ReviewStyle` 中实现 `update(name, icon, scenario, rules, enabled, sortOrder)` 方法：内部调用 `validateRules()`；若 `isDefault=true` 且入参 `enabled=false` 抛 `STYLE_DEFAULT_NOT_DISABLABLE`；不允许通过此方法修改 `isDefault` 字段
- [x] 2.5 在 `ReviewStyle` 中实现 `markDeleted()` 逻辑删除方法：若 `isDefault=true` 抛 `STYLE_DEFAULT_NOT_DELETABLE`
- [x] 2.6 在 `ReviewStyle` 中实现 `markAsDefault()` 与 `unmarkDefault()` 方法（仅供 Application 层在 set-default 事务内调用，不对外暴露）
- [x] 2.7 创建 `domain/.../reviewer/style/repository/ReviewStyleRepository.java` 接口（findById、findDefault、save、update、softDelete、findPageByCondition、existsByName、findAllEnabled、clearAllDefaultFlags）

## 3. Infrastructure 层

- [x] 3.1 创建 `infrastructure/.../reviewer/style/po/ReviewStylePO.java`（`@TableName("review_style")`、`@Version`、`@TableLogic`，字段与表结构对应；rules 字段为 String 类型存 JSON）
- [x] 3.2 创建 `infrastructure/.../reviewer/style/mapper/ReviewStyleMapper.java`（继承 `BaseMapper<ReviewStylePO>`）；新增方法 `clearAllDefaultFlags()` 使用 `@Update("UPDATE review_style SET is_default=0 WHERE is_default=1 AND deleted=0")`
- [x] 3.3 创建 `infrastructure/.../reviewer/style/assembler/ReviewStyleAssembler.java`：PO ↔ 聚合根双向转换；rules 字段使用 Jackson `ObjectMapper` 进行 JSON 序列化/反序列化；反序列化失败时抛 `STYLE_RULE_INVALID`
- [x] 3.4 实现 `infrastructure/.../reviewer/style/repository/ReviewStyleRepositoryImpl.java`：`findPageByCondition` 按 enabled 参数与角色组装 `LambdaQueryWrapper`，排序 `sortOrder ASC, id ASC`；`existsByName(name, excludeId)` 排除自身 id 和已删除记录；`findDefault()` 查询 `is_default=1 AND enabled=1 AND deleted=0` 单条；`clearAllDefaultFlags()` 委托 Mapper

## 4. Application 层

- [x] 4.1 创建 Command 对象：`CreateReviewStyleCommand`（name、icon、scenario、rules、sortOrder）、`UpdateReviewStyleCommand`（name、icon、scenario、rules、enabled、sortOrder、version）、`ReviewStyleQueryCommand`（page、size、enabled、currentUserRole）
- [x] 4.2 创建 `StyleRuleDTO` record（label、content）、`ReviewStyleDTO` record（id、name、icon、scenario、rules、enabled、isDefault、sortOrder、version、createdAt、updatedAt）、`ReviewStylePageResult`（total、items）
- [x] 4.3 实现 `ReviewStyleApplicationService.create()`：校验 name 非空、检查名称唯一性、调用 `ReviewStyle.create()`（强制 isDefault=false 即使入参带也忽略）、Repository 保存、返回 DTO
- [x] 4.4 实现 `ReviewStyleApplicationService.update()`：查找风格、检查名称唯一性（排除自身）、调用 `style.update()`（忽略入参的 isDefault 字段）、Repository 更新、返回 DTO
- [x] 4.5 实现 `ReviewStyleApplicationService.delete()`：查找风格、调用 `style.markDeleted()`（受默认风格保护）、Repository 软删
- [x] 4.6 实现 `ReviewStyleApplicationService.getById()`：查找风格、不存在抛 `STYLE_NOT_FOUND`、返回 DTO
- [x] 4.7 实现 `ReviewStyleApplicationService.listStyles()`：按角色决定是否强制 enabled=true（非 ADMIN 强制），分页查询，返回 `ReviewStylePageResult`
- [x] 4.8 实现 `ReviewStyleApplicationService.setDefault(id)`：`@Transactional`，查找目标风格 → 校验 `enabled=true`（否则抛 `PARAM_INVALID`）→ 调用 `repository.clearAllDefaultFlags()` → 调用 `style.markAsDefault()` → Repository 更新

## 5. API 层

- [x] 5.1 创建请求 DTO：`StyleRuleRequest`（@NotBlank label、@NotBlank content）、`CreateReviewStyleRequest`（@NotBlank name、icon、scenario、@NotEmpty @Size(min=4,max=8) rules、sortOrder）、`UpdateReviewStyleRequest`（@NotBlank name、icon、scenario、@NotEmpty @Size(min=4,max=8) rules、@NotNull enabled、sortOrder、@NotNull version）
- [x] 5.2 创建响应 DTO：`StyleRuleResponse`（label、content）、`ReviewStyleResponse`（id、name、icon、scenario、rules、enabled、isDefault、sortOrder、version、createdAt、updatedAt）
- [x] 5.3 实现 `ReviewStyleController`：`POST /api/v1/review-styles`（@RequireRole ADMIN）创建评审风格
- [x] 5.4 实现 `ReviewStyleController`：`PUT /api/v1/review-styles/{id}`（@RequireRole ADMIN）更新评审风格
- [x] 5.5 实现 `ReviewStyleController`：`DELETE /api/v1/review-styles/{id}`（@RequireRole ADMIN）逻辑删除
- [x] 5.6 实现 `ReviewStyleController`：`POST /api/v1/review-styles/{id}/set-default`（@RequireRole ADMIN）原子切换默认风格
- [x] 5.7 实现 `ReviewStyleController`：`GET /api/v1/review-styles/{id}`（@RequireRole SUBMITTER）查看详情
- [x] 5.8 实现 `ReviewStyleController`：`GET /api/v1/review-styles`（@RequireRole SUBMITTER）分页列表，含可选 enabled 筛选参数

## 6. 测试

- [x] 6.1 `ReviewStyleTest`（纯领域单元测试）：create 工厂默认值（isDefault=false）；validateRules 边界（3/4/8/9 条、label 或 content 为空）；update 触发校验；默认风格 update enabled=false 抛 `STYLE_DEFAULT_NOT_DISABLABLE`；默认风格 markDeleted 抛 `STYLE_DEFAULT_NOT_DELETABLE`；markAsDefault / unmarkDefault 行为
- [x] 6.2 `ReviewStyleApplicationServiceTest`：create 成功 + 名称重复 + 规则非法 + 忽略入参 isDefault
- [x] 6.3 `ReviewStyleApplicationServiceTest`：update 成功 + 乐观锁冲突 + 名称冲突（排除自身）+ 忽略入参 isDefault + 默认风格禁用受阻
- [x] 6.4 `ReviewStyleApplicationServiceTest`：delete 成功 + 默认风格不可删除 + 不存在
- [x] 6.5 `ReviewStyleApplicationServiceTest`：getById 存在/不存在
- [x] 6.6 `ReviewStyleApplicationServiceTest`：listStyles ADMIN 看全部 / SUBMITTER 仅看 enabled=true / 排序正确
- [x] 6.7 `ReviewStyleApplicationServiceTest`：setDefault 成功（清空其他 + 标记目标）+ 目标禁用不可设默认 + 不存在 + 切换默认后原默认可删除
- [x] 6.8 `ReviewStyleAssemblerTest`：rules JSON 序列化/反序列化正确，反序列化失败抛 `STYLE_RULE_INVALID`

## 7. 前端联调（基于 frontend/app.html 原型）

- [x] 7.1 创建 `frontend/js/review-style.js`：封装 `loadReviewStyles()`、`openCreateStyleModal()`、`openEditStyleModal()`、`submitStyleModal()`、`toggleStyle()`、`deleteStyle()`、`setDefaultStyle()`、`addRuleRow()`、`removeRuleRow()`，统一通过 `authFetch` 调用 `/api/v1/review-styles`
- [x] 7.2 修改 `frontend/app.html`：删除"评审风格"页面的硬编码静态卡片，替换为单一 `.review-style-list` 容器（由 JS 动态渲染，含 icon、name、isDefault 徽标、enabled 徽标、scenario、规则数量预览）；保留"+ 新建风格"按钮并绑定 `onclick="openCreateStyleModal()"`
- [x] 7.3 在 `frontend/app.html` 末尾增加新建/编辑评审风格 Modal（含 name/icon/scenario/sortOrder + 动态规则列表行，每行 label + content 输入项 + 删除按钮 + "+ 新增规则"按钮）；同步增加对应 Modal CSS；Modal 不含 `isDefault` 字段
- [x] 7.4 在 `navTo()` 函数末尾加入 `if (name === 'review-style') loadReviewStyles()` 钩子，切换到该页时自动加载列表
- [x] 7.5 在 `app.html` 引入 `<script src="js/review-style.js"></script>`（位于 auth.js 之后）
- [x] 7.6 角色差异渲染：ADMIN 看到所有风格 + 写操作按钮（编辑/启停/设为默认/删除）；非 ADMIN 仅渲染只读卡片；默认风格的"⭐ 设为默认"按钮显示为已选中态；点击默认风格的删除/禁用按钮 SHALL 给出友好提示
