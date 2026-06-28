## MODIFIED Requirements

### Requirement: 提交 PRD 评审
系统 SHALL 提供 `POST /api/v1/prds/{id}/submit` 接口，需登录，仅 PRD 本人可提交，将 PRD 状态从 DRAFT 变为 SUBMITTED，同时创建版本快照。

在状态转移之前 SHALL 先执行输入门槛校验：title 与 content 必须满足最低字数；content 必须包含至少 2 个核心章节（背景 / 目标 / 功能设计，详见 "PRD 提交输入门槛" requirement）。校验失败时 SHALL 直接抛对应 BizException，不进入状态机，不创建快照。

#### Scenario: 提交成功
- **WHEN** 本人对 DRAFT 状态的 PRD 调用 submit，且 title/content 满足门槛、含必要章节
- **THEN** PRD status MUST 变为 SUBMITTED，`prd_version` 表 MUST 新增一条快照记录，返回更新后的 PrdResponse

#### Scenario: INITIALIZING 状态不允许提交
- **WHEN** 对 INITIALIZING 状态的 PRD 调用 submit（AI 尚未完成）
- **THEN** 系统 MUST 返回错误码 `10004`（操作不允许）

#### Scenario: 非 DRAFT 状态提交
- **WHEN** 对 SUBMITTED 或更高状态的 PRD 调用 submit
- **THEN** 系统 MUST 返回错误码 `10004`（操作不允许）

#### Scenario: 非本人提交
- **WHEN** 非 authorId 用户调用 submit
- **THEN** 系统 MUST 返回错误码 `20002`（无权限）

#### Scenario: title 太短被拒绝
- **WHEN** 本人提交 title 有效字符数 < 5 的 PRD
- **THEN** 系统 MUST 返回错误码 `30002`（PRD_CONTENT_TOO_SHORT），错误消息含 "title"、当前字数与阈值；PRD 状态 MUST 保持 DRAFT

#### Scenario: content 太短被拒绝
- **WHEN** 本人提交 content 有效字符数 < 200 的 PRD
- **THEN** 系统 MUST 返回错误码 `30002`（PRD_CONTENT_TOO_SHORT），错误消息含 "content"、当前字数与阈值；PRD 状态 MUST 保持 DRAFT

#### Scenario: 缺少必要章节被拒绝
- **WHEN** 本人提交内容字数达标但只含 0 或 1 个核心章节的 PRD
- **THEN** 系统 MUST 返回错误码 `30003`（PRD_MISSING_REQUIRED_SECTION），错误消息明确列出缺失的章节名（中文显示名）；PRD 状态 MUST 保持 DRAFT

---

## ADDED Requirements

### Requirement: PRD 提交输入门槛
系统 SHALL 在 `POST /api/v1/prds/{id}/submit` 用例中、状态机转移之前，执行下列输入门槛校验。这套校验是 PRD 进入评审环节的前置质量门槛——草稿编辑（create/update）不触发，仅在 submit 时触发。

**字数门槛（PRD_CONTENT_TOO_SHORT, 30002）：**
- `title` 的有效字符数（去除全部空白后）SHALL ≥ 5
- `content` 的有效字符数（去除全部空白后）SHALL ≥ 200
- 阈值定义在 domain 层 `PrdInputValidator` 的 public static final 常量中
- 错误消息 SHALL 包含字段名（title/content）、当前字符数与阈值字符数

**章节门槛（PRD_MISSING_REQUIRED_SECTION, 30003）：**
- 系统 SHALL 扫描 content 中的 markdown 一级/二级标题（`# xxx` / `## xxx`），抽取标题文本
- 系统 SHALL 与三个 RequiredSection 同义词集合比对：BACKGROUND（背景/概述/Overview/Background）、GOAL（目标/产品目标/Goal/Goals/Objective/Objectives）、DESIGN（功能设计/功能/功能方案/方案/Solution/Design）
- 匹配时 SHALL 忽略大小写、忽略首尾空白
- 至少命中 2 个 RequiredSection 才通过；否则抛 `PRD_MISSING_REQUIRED_SECTION`
- 错误消息 SHALL 列出**缺失**的 RequiredSection 中文显示名（如 "目标, 功能设计"）

#### Scenario: 字数有效字符不含空白
- **WHEN** title="     hi     "（连尾共 13 字符，但有效仅 2 字符）调 submit
- **THEN** 系统 MUST 返回 `PRD_CONTENT_TOO_SHORT`，错误消息提示当前 2 字符 < 阈值 5

#### Scenario: 同义词识别 — 英文章节
- **WHEN** content 含 `## Background` 与 `## Goals` 两个 H2 标题，字数达标
- **THEN** 系统 MUST 识别出 BACKGROUND 与 GOAL，通过章节门槛

#### Scenario: 同义词识别 — 中文章节
- **WHEN** content 含 `# 背景` 与 `# 功能设计` 两个 H1 标题，字数达标
- **THEN** 系统 MUST 识别出 BACKGROUND 与 DESIGN，通过章节门槛

#### Scenario: 同义词识别 — 不区分大小写
- **WHEN** content 含 `# BACKGROUND` 与 `# objective`
- **THEN** 系统 MUST 识别出 BACKGROUND 与 GOAL，通过章节门槛

#### Scenario: 缺失章节错误消息列出中文名
- **WHEN** content 仅含 `# 背景` 一个章节，字数达标
- **THEN** 系统 MUST 返回 `PRD_MISSING_REQUIRED_SECTION`，错误消息 MUST 含 "目标" 与 "功能设计"（缺失项），可选附 "已识别：背景"

#### Scenario: URL 路径创建的 PRD 在 submit 时同样过门槛
- **WHEN** 用户通过 `POST /api/v1/prds/from-url` 创建 PRD，AI 摘要后转为 DRAFT，content 仅 100 字符
- **THEN** 该 PRD 调 submit MUST 与手动创建的 PRD 走完全相同的校验，返回 `PRD_CONTENT_TOO_SHORT`；用户可先调 `PUT /api/v1/prds/{id}` 扩充内容后再 submit
