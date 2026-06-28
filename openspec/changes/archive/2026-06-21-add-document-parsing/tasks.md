## 1. 依赖与配置

- [x] 1.1 在 parent `pom.xml` <properties> 加 `<tika.version>2.9.2</tika.version>`,在 <dependencyManagement> 加 `tika-core` 与 `tika-parsers-standard-package`(版本统一引用)
- [x] 1.2 在 `infrastructure/pom.xml` 加 `tika-core` 与 `tika-parsers-standard-package` 依赖(不写版本)
- [x] 1.3 在 `bootstrap/src/main/resources/application.yml` 加 multipart 配置:
  ```yaml
  spring:
    servlet:
      multipart:
        max-file-size: 10MB
        max-request-size: 12MB
        enabled: true
  ```

## 2. Domain 层

- [x] 2.1 在 `domain/.../common/exception/ErrorCode.java` 30000 段加 3 个错误码:
  ```java
  PRD_FILE_TYPE_UNSUPPORTED(30006, "不支持的文件类型"),
  PRD_FILE_TOO_LARGE(30007, "文件超过 10MB 限制"),
  PRD_FILE_PARSE_FAILED(30008, "文件解析失败"),
  ```
- [x] 2.2 在 `domain/.../ai/service/AiService.java` 接口加方法:
  ```java
  /** 解析文件(PDF/Word/Markdown/纯文本) → AI 摘要。文件类型/大小/解析失败抛对应 BizException。 */
  SummarizeResult summarizeFromFile(byte[] bytes, String filename);
  ```

## 3. Infrastructure 层

- [x] 3.1 创建 `infrastructure/.../ai/DocumentParser.java`(`@Component`):
  - 持有 `Tika tika = new Tika();`(自动初始化 AutoDetectParser)
  - `public static final Set<String> SUPPORTED_MIME = Set.of("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword", "text/plain", "text/markdown", "text/x-markdown");`
  - 方法 `String detectMime(byte[] bytes, String filename)` → `tika.detect(bytes, filename)` 返回 MIME
  - 方法 `String parseText(byte[] bytes, String filename)`:
    - 检测 MIME,不在白名单抛 `BizException(PRD_FILE_TYPE_UNSUPPORTED, "...: " + mime)`
    - 用 `tika.parseToString(InputStream)` 解析
    - 若结果 `isBlank() || length() < 10` 抛 `BizException(PRD_FILE_PARSE_FAILED, "可能是扫描件,请提供文字版")`
    - 其他 IOException/TikaException 抛 `BizException(PRD_FILE_PARSE_FAILED, "解析失败:" + 摘要)`
- [x] 3.2 在 `infrastructure/.../ai/AiServiceImpl.java`:
  - 构造函数注入 `DocumentParser documentParser`
  - 实现 `summarizeFromFile(byte[] bytes, String filename)`:
    - `String text = documentParser.parseText(bytes, filename);`
    - `return summarizeText(text);`(复用现有逻辑)
    - 日志:`log.info("[AI] summarizeFromFile filename={} bytes={}", filename, bytes.length);`

## 4. Application 层

- [x] 4.1 创建 `application/.../prd/CreatePrdFromFileCommand.java` record:
  ```java
  public record CreatePrdFromFileCommand(byte[] bytes, String filename, Long currentUserId) {}
  ```
- [x] 4.2 在 `PrdApplicationService` 加 `createFromFile(CreatePrdFromFileCommand cmd)`:
  - 校验 `cmd.bytes() != null && cmd.bytes().length > 0` 否则抛 `PARAM_INVALID`
  - 校验大小 `cmd.bytes().length <= 10 * 1024 * 1024` 否则抛 `PRD_FILE_TOO_LARGE`
  - 调 `SummarizeResult result = aiService.summarizeFromFile(cmd.bytes(), cmd.filename());`
  - `Prd prd = Prd.createFromManual(result.title(), result.content(), cmd.currentUserId());`(走 DRAFT,不走 INITIALIZING)
  - `Prd saved = prdRepository.save(prd);`
  - 返回 `toDTO(saved)`
  - 日志:`log.info("[PRD] createFromFile filename={} prdId={} bytes={}", filename, saved.getId(), bytes.length);`

## 5. API 层

- [x] 5.1 在 `PrdController` 加 endpoint:
  ```java
  @PostMapping(value = "/from-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public PrdResponse createFromFile(@RequestPart("file") MultipartFile file) {
      if (file.isEmpty()) throw new BizException(ErrorCode.PARAM_INVALID, "上传文件为空");
      try {
          byte[] bytes = file.getBytes();
          Long userId = AuthContext.requireCurrentUserId();
          PrdDTO dto = prdService.createFromFile(
              new CreatePrdFromFileCommand(bytes, file.getOriginalFilename(), userId));
          return PrdResponse.from(dto);
      } catch (IOException e) {
          throw new BizException(ErrorCode.PRD_FILE_PARSE_FAILED, "读取上传文件失败: " + e.getMessage());
      }
  }
  ```
- [x] 5.2 在 `GlobalExceptionHandler` 加:
  ```java
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
      log.warn("[Upload] 文件超限: {}", ex.getMessage());
      return Result.error(ErrorCode.PRD_FILE_TOO_LARGE);
  }
  ```

## 6. 测试 - DocumentParser

- [x] 6.1 在 `infrastructure/src/test/resources/samples/` 准备测试样本:
  - `sample.pdf` — 用 Tika 自带或简单工具生成一份"# 标题\n正文 100+ 字符"的 PDF(可用 itextpdf 或直接放预先准备的小 PDF;~5 KB)
  - `sample.docx` — 同上,Word 文档
  - `sample.txt` — 纯文本
  - `sample.md` — markdown
  - `not-supported.zip` — 一个 zip 文件(或假装 zip 内容头 `PK\x03\x04...`)
- [x] 6.2 创建 `DocumentParserTest`(infrastructure 单元测试):
  - `detectMime` PDF 字节 → 返回 "application/pdf"
  - `detectMime` docx 字节 → 返回 docx MIME
  - `parseText` PDF → 返回非空文本
  - `parseText` docx → 返回非空文本
  - `parseText` txt → 直接返回原文
  - `parseText` md → 返回原文
  - `parseText` zip → 抛 PRD_FILE_TYPE_UNSUPPORTED
  - `parseText` 极短内容(模拟扫描件)→ 抛 PRD_FILE_PARSE_FAILED

## 7. 测试 - AiServiceImpl

- [x] 7.1 在 `AiServiceImplTest` 新增 `summarizeFromFile` 用例:
  - Mock `DocumentParser.parseText` 返回 "测试文本内容..."
  - Mock ChatClient 链返回合法 JSON `{"title":"测试标题","content":"测试摘要"}`
  - 调 `summarizeFromFile(bytes, "test.pdf")` → 验证返回的 `SummarizeResult` title/content 正确
  - 验证 `documentParser.parseText` 被调用 1 次,参数透传
- [x] 7.2 异常路径:
  - Mock `DocumentParser.parseText` 抛 `PRD_FILE_TYPE_UNSUPPORTED` → 验证向上传播,不调 ChatClient

## 8. 测试 - PrdApplicationService

- [x] 8.1 在 `PrdApplicationServiceTest` 新增 4 个用例:
  - `createFromFile_success` — Mock AiService.summarizeFromFile 返回合法 SummarizeResult,验证 prd 保存为 DRAFT,sourceUrl=null
  - `createFromFile_emptyBytes` — 传入空 byte[] → 抛 PARAM_INVALID
  - `createFromFile_tooLarge` — 传入 11MB byte[] → 抛 PRD_FILE_TOO_LARGE,不调 aiService
  - `createFromFile_aiServiceFails` — Mock AiService 抛 BizException 直接向上传播,无 prd 保存

## 9. 前端

- [x] 9.1 在 `frontend/app.html` 找到 PRD 创建相关的位置(由 #5 的前端实现位置定),在创建表单上方加按钮:
  ```html
  <div style="margin-bottom:12px">
    <button class="btn btn-outline btn-sm" onclick="document.getElementById('prd-upload-input').click()">
      <svg ...refresh-style svg... aria-hidden="true">📎</svg>
      <span>上传文档</span>
    </button>
    <input type="file" id="prd-upload-input" accept=".pdf,.docx,.doc,.txt,.md"
           style="display:none" onchange="uploadPrdFile(this.files[0])">
    <span style="font-size:12px;color:var(--text-3);margin-left:8px">支持 PDF/Word/Markdown/纯文本,≤ 10MB</span>
  </div>
  ```
- [x] 9.2 创建 `frontend/js/prd-upload.js`(或在已有 PRD JS 里加):
  ```js
  async function uploadPrdFile(file) {
    if (!file) return;
    if (file.size > 10 * 1024 * 1024) { alert('文件过大,最大 10MB'); return; }
    const formData = new FormData();
    formData.append('file', file);
    const token = getToken();
    try {
      const resp = await fetch(`${API_BASE}/api/v1/prds/from-file`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` },  // 不设 Content-Type,浏览器自动加 boundary
        body: formData
      });
      const json = await resp.json();
      if (json.code !== 0) throw new Error(json.message);
      // 成功:跳转或回填到创建表单
      alert('上传成功,已创建 PRD 草稿 (id=' + json.data.id + ')');
      location.reload();  // 或更优雅:回填到 form,让用户预览编辑
    } catch (e) {
      alert('上传失败: ' + e.message);
    }
  }
  window.uploadPrdFile = uploadPrdFile;
  ```
- [x] 9.3 在 `app.html` 末尾引入 `<script src="js/prd-upload.js"></script>`

## 10. 集成验证(手工)

- [x] 10.1 启动应用:`DEEPSEEK_API_KEY=... mvn -pl bootstrap spring-boot:run`
- [x] 10.2 准备一份小 PDF(可用项目里的 sample.pdf,或在 Mac 上 `textutil -convert pdf README.md`)
- [x] 10.3 用 curl 测:
  ```bash
  TOKEN=$(curl -s ...)
  curl -X POST http://localhost:8080/api/v1/prds/from-file \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@/tmp/sample.pdf"
  ```
  预期:`code=0`,返回 `PrdResponse` 含 AI 摘要的 title/content,status=DRAFT
- [x] 10.4 上传 .zip → 预期 30006
- [x] 10.5 上传 > 10MB 文件 → 预期 30007(Spring 拦截)
- [x] 10.6 前端在 PRD 创建页点"📎 上传文档" → 选 PDF → 提示"上传成功" → 列表能看到新 PRD

## 11. 归档准备

- [x] 11.1 跑全量测试 `mvn clean test`,确认无回归
- [x] 11.2 合并 spec 增量到 `openspec/specs/prd-storage/spec.md`(ADDED 新 requirement)+ `openspec/specs/ai-infrastructure/spec.md`(MODIFIED AiService 接口)
- [x] 11.3 归档:`mv openspec/changes/add-document-parsing openspec/changes/archive/$(date +%Y-%m-%d)-add-document-parsing`
- [x] 11.4 更新 `openspec/roadmap.md`:#7 行改 ✅ DONE
