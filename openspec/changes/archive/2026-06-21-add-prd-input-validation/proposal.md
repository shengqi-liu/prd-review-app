## Why

#5 (`add-prd-storage`) 在 DRAFT 阶段允许 PRD 内容非常松散——只校验 `@NotBlank`,URL 路径甚至允许 title/content 临时为空(INITIALIZING 状态)。这对草稿期是合理的,但**提交评审**(`POST /api/v1/prds/{id}/submit`)时要把 PRD 交给 AI 评审,垃圾输入(几个字、缺章节)会:

- 浪费 LLM token(每次评审都是真金白银的调用)
- AI 给不出有意义的反馈(没内容怎么评)
- 评审报告噪声大,降低用户对系统的信任

需要一道"内容质量门槛":提交前确认 PRD **写得像个 PRD**。`ErrorCode.PRD_CONTENT_TOO_SHORT(30002)` 和 `PRD_MISSING_REQUIRED_SECTION(30003)` 在 #5 阶段就预留了,本 change 让它们真正生效。

## What Changes

- **新增最低字数门槛**:title ≥ 5 字符,content ≥ 200 字符;空白(全是空格/换行/tab)不计入有效字符数;不足抛 `PRD_CONTENT_TOO_SHORT`,错误信息明确指出哪个字段、当前字数、阈值
- **新增章节解析**:扫描 content 里的 markdown 一级/二级标题(`# ...` 或 `## ...`),抽取章节名;若不包含「背景 / 目标 / 功能设计」三个核心章节中**至少 2 个**,抛 `PRD_MISSING_REQUIRED_SECTION`,错误信息提示**缺哪些**章节
- **章节同义词识别**(不区分大小写,允许首尾空格):
  - 背景:`背景` `概述` `Overview` `Background`
  - 目标:`目标` `产品目标` `Goal` `Goals` `Objective` `Objectives`
  - 功能设计:`功能设计` `功能` `功能方案` `方案` `Solution` `Design`
- **校验时机:仅在 submit 用例触发**——草稿期(create/update)不影响;通过校验后才执行 `prd.submit()` 状态转移

## Capabilities

### New Capabilities

(无)

### Modified Capabilities

- `prd-storage`:`POST /api/v1/prds/{id}/submit` 用例增加输入门槛 + 章节校验前置;ADDED 新 requirement 描述校验规则

## Impact

- **代码**:
  - `domain/.../prd/validation/PrdInputValidator.java` — 新增,纯静态方法,无 Spring 依赖
  - `domain/.../prd/validation/RequiredSection.java` — 新增枚举(BACKGROUND/GOAL/DESIGN + 同义词集合)
  - `application/.../prd/service/PrdApplicationService.submit()` — 在 `prd.submit()` 之前调验证器
- **spec**:`openspec/specs/prd-storage/spec.md` 通过 MODIFIED 改一条 + ADDED 加一条 requirement
- **测试**:
  - `PrdInputValidatorTest` 纯单元测试:字数边界、空白处理、章节同义词识别、缺章节提示文案
  - `PrdApplicationServiceTest.submitPrd` 增加 3 个用例:title 太短、content 太短、缺必要章节
- **无 API 变更**(只新增校验,响应错误码已预留)、**无前端变更**(错误码已存在,前端 toast 已能显示)、**无 DB schema 变更**

## 与 URL 路径创建 PRD 的关系

#5 (`add-prd-storage`) 已实现 URL 路径创建 PRD —— `POST /api/v1/prds/from-url` 通过 SSE 流式触发 `DocumentFetcher` 抓 HTML → Jsoup 提取正文 → `AiService.summarizeFromUrl()` AI 摘要 → 落到 DRAFT 状态。

**本 change 不改 URL 路径的创建流程**——AI 摘要给出的 title/content 可能字数不够或缺章节,这是 AI 的能力问题,不是用户操心的事。允许 URL 路径在 DRAFT 阶段先落地"低质内容",用户随后用 `PUT /api/v1/prds/{id}` 编辑补全(扩内容、加章节),最后 submit 时统一过 #6 的门槛。

关键设计:**门槛只在 submit 拦截**,与"如何创建"无关。URL 路径与手动路径在 submit 时走完全相同的校验。

## Out of Scope

- 不改 PRD CRUD 接口契约(只在 submit 时拦截,create/update 仍宽松)
- 不引入 Markdown 解析库(commonmark / flexmark)——简单正则够用
- 不做章节**顺序**校验(只验存在与否;顺序由 PM 自己定)
- 不做内容**质量评分**(那是 AI 评审的事,不是字数门槛能解决的)
- 不做 PDF/Word 文档解析(#7 的范围)
- 不把阈值做成"按 reviewer 配置"(全局常量足够,需要时再扩展)
