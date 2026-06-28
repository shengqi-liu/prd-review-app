## ADDED Requirements

### Requirement: 从文件创建 PRD
系统 SHALL 提供 `POST /api/v1/prds/from-file` 接口（multipart/form-data，字段名 `file`），支持上传 PDF / Word / Markdown / 纯文本，自动解析为文本后调 AI 摘要，落到 DRAFT 状态。需要登录；同步返回 `PrdResponse`。

**支持的文件类型**：
- `application/pdf`
- `application/vnd.openxmlformats-officedocument.wordprocessingml.document`（.docx）
- `application/msword`（.doc）
- `text/plain`（.txt）
- `text/markdown` / `text/x-markdown`（.md）

类型检测 SHALL 基于 Tika 的内容魔数 + 文件名双重判定，不只看后缀。

**文件大小**：单文件 SHALL ≤ 10 MB；Spring `multipart.max-file-size` 与 service 层各拦一道，超限抛 `PRD_FILE_TOO_LARGE`。

**错误码**：
- `PRD_FILE_TYPE_UNSUPPORTED(30006)` — MIME 不在白名单
- `PRD_FILE_TOO_LARGE(30007)` — 文件超 10 MB
- `PRD_FILE_PARSE_FAILED(30008)` — Tika 解析失败 / 解析后文本过短（疑似扫描件无文字层）

#### Scenario: 上传 PDF 成功创建 PRD
- **WHEN** 已登录用户上传一份内容正常的 PDF（≥ 100 字符可识别文字）
- **THEN** 系统 MUST 同步返回 `PrdResponse`，`status=DRAFT`，`title` 与 `content` 由 AI 摘要填充；`sourceUrl` MUST 为 null（区别于 URL 路径）

#### Scenario: 上传 Word 文档成功创建 PRD
- **WHEN** 已登录用户上传一份 `.docx` 文件
- **THEN** 系统 MUST 解析为文本后 AI 摘要，落到 DRAFT 状态

#### Scenario: 上传纯文本 / Markdown 成功创建 PRD
- **WHEN** 已登录用户上传 `.txt` 或 `.md`
- **THEN** 系统 MUST 直接把文本送 AI 摘要，落到 DRAFT 状态

#### Scenario: 不支持的文件类型被拒绝
- **WHEN** 已登录用户上传 `.exe` / `.zip` / `.pptx` 等不在白名单的文件
- **THEN** 系统 MUST 返回 `PRD_FILE_TYPE_UNSUPPORTED(30006)`，错误消息含检测到的 MIME 类型

#### Scenario: 文件超 10 MB 被拒绝
- **WHEN** 已登录用户上传 > 10 MB 的文件
- **THEN** 系统 MUST 返回 `PRD_FILE_TOO_LARGE(30007)`（来自 Spring 拦截或 service 层兜底）

#### Scenario: 解析失败（疑似扫描件 / 损坏文件）
- **WHEN** 已登录用户上传一份扫描件 PDF（无文字层）或损坏的 docx
- **THEN** 系统 MUST 返回 `PRD_FILE_PARSE_FAILED(30008)`，错误消息提示"可能是扫描件，请提供文字版"或"文件无法解析"

#### Scenario: 未登录上传被拒绝
- **WHEN** 未携带 JWT 调用上传接口
- **THEN** 系统 MUST 返回 `UNAUTHORIZED(20001)`

#### Scenario: 上传后再 submit 走 #6 输入门槛
- **WHEN** 上传成功落 DRAFT 后，用户直接 POST submit（不补章节）
- **THEN** 系统 MUST 与手动/URL 路径同样过 `#6` 输入门槛校验，可能因缺章节返回 `PRD_MISSING_REQUIRED_SECTION(30003)`

---

## MODIFIED Requirements

### Requirement: AI 摘要服务（AiService）
系统 SHALL 提供 `AiService` 接口暴露 AI 摘要能力，屏蔽底层 Spring AI 与 LLM provider 细节。接口包含：

- `summarizeFromUrl(String url)` — 抓取 URL 内容后摘要（#4.5 已实现）
- `summarizeText(String rawText)` — 对已有文本摘要（#4.5 已实现）
- `streamCompletion(String prompt)` / `streamCompletion(String system, String user)` — 流式补全（#9 / fix-kb-sync-correctness 实现）
- `summarizeFromFile(byte[] bytes, String filename)` — 解析文件后摘要（#7 新增）

`summarizeFromFile` 的实现 SHALL：
1. 用 Tika `AutoDetectParser` 基于内容 + 文件名检测 MIME 类型
2. 若 MIME 不在白名单（PDF / DOCX / DOC / TXT / MD），抛 `BizException(PRD_FILE_TYPE_UNSUPPORTED)`
3. 用对应 parser 解析为纯文本；若解析失败或结果空白/过短，抛 `BizException(PRD_FILE_PARSE_FAILED)`
4. 调 `summarizeText(parsedText)` 复用现有 AI 摘要流程，返回 `SummarizeResult`

#### Scenario: summarizeFromFile 解析 PDF 后调用 summarizeText
- **WHEN** 传入合法 PDF 字节流
- **THEN** Tika MUST 检测为 `application/pdf`、解析为纯文本，并调 `summarizeText` 返回带 title/content 的 `SummarizeResult`

#### Scenario: summarizeFromFile 拒绝白名单外类型
- **WHEN** 传入 `.zip` 文件字节流
- **THEN** MUST 抛 `BizException(PRD_FILE_TYPE_UNSUPPORTED)`，不调用 ChatClient

#### Scenario: summarizeFromFile 文本过短抛失败
- **WHEN** Tika 解析后得到 < 10 字符的纯文本（疑似扫描件 PDF）
- **THEN** MUST 抛 `BizException(PRD_FILE_PARSE_FAILED)`，错误消息提示"可能是扫描件"

#### Scenario: summarizeFromFile 与 summarizeFromUrl 行为对称
- **WHEN** 同一份内容通过文件上传和 URL 抓取分别调用
- **THEN** 两者返回的 `SummarizeResult` MUST 是等价的（同一份原始文本走相同的 summarizeText 流程）
