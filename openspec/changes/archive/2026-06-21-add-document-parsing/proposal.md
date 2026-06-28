## Why

#5 已实现两种 PRD 创建路径:
- **手动输入** `POST /api/v1/prds` — 用户在前端粘贴 title + content
- **URL 抓取** `POST /api/v1/prds/from-url`(SSE 流式)— 给一个公开网页地址,系统拉 HTML + AI 摘要

但**真实工作流**里,PM 大多数 PRD 文档已经存在 `*.pdf` / `*.docx` 形态(从 wiki 导出、协作平台下载、邮件附件等)。当前要导入只能复制粘贴——格式经常崩、长文容易漏段。补一条**文件上传**路径,让 PM 直接拖文档进来就完成 PRD 草稿创建。

## What Changes

- **新增上传接口** `POST /api/v1/prds/from-file`(multipart/form-data,字段名 `file`),同步返回 `PrdResponse`
- **支持格式**:PDF (`.pdf`)、Word (`.docx` / `.doc`)、Markdown (`.md`)、纯文本 (`.txt`);用 Tika 的 MIME 检测,不只看后缀
- **文件大小限制 10 MB**(在 Spring multipart 配置 + service 层各拦一道)
- **解析后流程与 URL 路径一致**:Tika 抽出原始文本 → `AiService.summarizeFromFile()` 调 AI 摘要 → 落 DRAFT 状态
- **3 个新错误码**:`PRD_FILE_TYPE_UNSUPPORTED(30006)`、`PRD_FILE_TOO_LARGE(30007)`、`PRD_FILE_PARSE_FAILED(30008)`
- **新依赖**:`org.apache.tika:tika-core` + `tika-parsers-standard-package`(parent pom 集中管理版本)
- **前端**:`app.html` 创建页加 `📎 上传文档` 按钮,选文件 → 调接口 → 跳到编辑页让用户补章节(`#6` 门槛在 submit 时拦)

## Capabilities

### New Capabilities

(无)

### Modified Capabilities

- `prd-storage`:新增"从文件创建 PRD"requirement;复用 `ai-infrastructure` 的 AI 摘要能力
- `ai-infrastructure`:`AiService` 接口扩展 `summarizeFromFile(bytes, filename)` 方法

## Impact

- **依赖**:
  - parent `pom.xml` <dependencyManagement> 加 Tika BOM 或固定版本(2.9.x)
  - `infrastructure/pom.xml` 加 `tika-core` + `tika-parsers-standard-package` 两个依赖
- **代码**:
  - `domain/.../ai/service/AiService.java` — 接口加 `summarizeFromFile(byte[] bytes, String filename)`
  - `domain/.../common/exception/ErrorCode.java` — 加 3 个错误码
  - `infrastructure/.../ai/DocumentParser.java` — 新增,基于 Tika `AutoDetectParser`
  - `infrastructure/.../ai/AiServiceImpl.java` — 加 `summarizeFromFile` 实现(parse → summarizeText 复用)
  - `application/.../prd/service/PrdApplicationService.java` — 加 `createFromFile(byte[], String, Long)` 用例
  - `application/.../prd/CreatePrdFromFileCommand.java` — 新增 record
  - `api/.../prd/controller/PrdController.java` — 加 `POST /api/v1/prds/from-file` endpoint(`@RequestPart MultipartFile`)
- **配置**:`bootstrap/.../application.yml` 加 `spring.servlet.multipart.max-file-size: 10MB` + `max-request-size: 12MB`
- **前端**:`app.html` PRD 创建页加 `📎 上传文档` 按钮 + 隐藏 `<input type="file">`;`reviewer.js` 或新文件 `prd-upload.js` 处理 `FormData` 提交
- **测试**:
  - `DocumentParserTest`(infrastructure)— 用 `src/test/resources/sample.pdf` / `sample.docx` / `sample.txt` 验证解析
  - `AiServiceImplTest.summarizeFromFile`(infrastructure)— Mock Tika + ChatClient,验证 parse → summarize 链路
  - `PrdApplicationServiceTest.createFromFile`(application)— 覆盖文件类型不支持、文件太大、解析失败、正常成功 4 个场景
- **无 DB schema 变更**

## Out of Scope

- **不做 OCR**——扫描件 PDF 没有文字层,Tika 会返回空白,用户自行处理(后续 change 如有需求再加 tesseract)
- **不做结构化章节抽取**——Tika 输出纯文本即可,章节由 #6 的 `PrdInputValidator` 在 submit 时校验
- **不做 SSE 上传进度**——10MB 内的文件 + AI 摘要,30s 内同步响应足够
- **不支持 `.pptx` / `.xlsx`**——PRD 一般是 Word/PDF,演示稿场景另议
- **不上传到对象存储**——文件读完即丢,只保留解析后的文本(节省存储,避免合规问题)
- **不与 URL 路径合并接口**——multipart 和 JSON 是两种 Content-Type,合并反而绕
