## Context

#5 已落地两条 PRD 创建路径(手动 + URL),`AiService` 已具备"文本进 → 摘要出"的核心能力。本 change 补**文件上传**这条最常用的路径,基础设施已经齐备 90%——只需要 Tika 抽文本 + 复用现有 AI 摘要。

技术上最大的取舍点是 **Tika 依赖体积**和**MIME 检测策略**。

## Goals / Non-Goals

**Goals:**
- 一行 `POST /api/v1/prds/from-file` 上传 PDF/Word/Markdown/纯文本 → 自动落 DRAFT 状态 PRD
- 文件类型/大小/解析失败三类错误清晰可观测(错误码 + 友好消息)
- 复用 #4.5 的 AI 摘要逻辑,不重复造轮子
- 同步响应,前端不需要 SSE/轮询逻辑

**Non-Goals:**
- 不做 OCR(扫描件无文字层)
- 不做章节结构化(交给 #6 在 submit 时校验)
- 不做异步上传/进度回报
- 不持久化原始文件(读完即丢)
- 不支持 `.pptx` `.xlsx` `.epub` 等其他格式

## Decisions

### D1:Tika 选 `tika-parsers-standard-package` 还是更精细组合?

**选择**:`tika-parsers-standard-package`(大包,涵盖 PDF/Office/HTML/text 等几十种格式)。

**理由**:
- 项目主要支持 PDF + Word,但 standard 包额外带的 HTML/text 解析对未来扩展(比如 wiki 导出 HTML)零成本可用
- 精细组合(只引 `tika-parser-pdf-module` + `tika-parser-microsoft-module`)能省 ~30% 体积,但每个 module 还有传递依赖,实际省不了多少
- standard 包是 Tika 官方推荐起步方式,文档/示例丰富

**备选**:`tika-core` + 自选 parser module — 增加维护成本,本场景不值

### D2:MIME 检测策略 — Tika 自动 vs 后缀映射

**选择**:Tika 的 `AutoDetectParser` + `TikaConfig` 默认 detector(基于内容魔数 + 后缀双重判定)。

```java
Tika tika = new Tika();
String mimeType = tika.detect(bytes, originalFilename); // 同时看内容和文件名
```

**理由**:
- 仅看后缀不安全(用户可能把 `.exe` 改名为 `.pdf`),仅看内容不准确(纯文本可能被检测为 `application/octet-stream`)
- Tika 的双重检测把误判降到最低
- 一次检测的结果用于:① 拒绝不支持类型 ② 选择 parser

**备选**:Apache `MimeMagic` / `URLConnection.guessContentTypeFromStream` — 精度低,不推荐

### D3:支持类型白名单

```java
private static final Set<String> SUPPORTED_MIME = Set.of(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",  // .docx
    "application/msword",                                                          // .doc
    "text/plain",
    "text/markdown",
    "text/x-markdown"
);
```

**理由**:Tika 能解析的远不止这些,但允许任意类型会增加攻击面(XML 解析漏洞、HTML 嵌入脚本等)。白名单聚焦核心需求。

不在白名单内的文件 → 抛 `PRD_FILE_TYPE_UNSUPPORTED(30006)`,错误消息含检测到的 MIME 类型方便排查。

### D4:文件大小限制 — 三道防线

| 层 | 机制 | 限制 | 触发响应 |
|---|---|---|---|
| Spring MVC | `spring.servlet.multipart.max-file-size` | 10MB | 抛 `MaxUploadSizeExceededException`,由 GlobalExceptionHandler 转 `PRD_FILE_TOO_LARGE` |
| Spring MVC | `spring.servlet.multipart.max-request-size` | 12MB | 同上(留 2MB 余量给其他 form 字段) |
| Service 层 | `validateFileSize(bytes)` | 10MB | 直接抛 `PRD_FILE_TOO_LARGE`(防止绕过 Spring 的极端场景) |

**理由**:Spring 层早拦避免大文件占用内存;Service 层做兜底防御。`GlobalExceptionHandler` 加一条 `@ExceptionHandler(MaxUploadSizeExceededException.class)` 转标准错误响应。

### D5:领域接口扩展 — `AiService.summarizeFromFile`

```java
// AiService.java 增加
SummarizeResult summarizeFromFile(byte[] bytes, String filename);
```

实现层:
```java
@Override
public SummarizeResult summarizeFromFile(byte[] bytes, String filename) {
    // 1. Tika 检测 MIME → 白名单校验(抛 PRD_FILE_TYPE_UNSUPPORTED)
    // 2. Tika 解析 → 纯文本
    // 3. 文本空白/过短 → 抛 PRD_FILE_PARSE_FAILED(可能是扫描件)
    // 4. 复用 summarizeText(rawText)
}
```

**理由**:
- 接口语义对称:`summarizeFromUrl(String)` / `summarizeFromFile(byte[], String)`
- 业务层调用方不感知 Tika,只关心"喂文件得摘要"
- 错误码归在 PRD 域(30006/30007/30008)而非 AI 域(99997),因为这是上传场景的业务错误

### D6:Controller 实现 — `MultipartFile` 转 `byte[]`

```java
@PostMapping("/from-file")
public PrdResponse createFromFile(@RequestPart("file") MultipartFile file) {
    if (file.isEmpty()) throw new BizException(ErrorCode.PARAM_INVALID, "文件为空");
    try {
        byte[] bytes = file.getBytes();
        Long userId = AuthContext.requireCurrentUserId();
        PrdDTO dto = prdService.createFromFile(
            new CreatePrdFromFileCommand(bytes, file.getOriginalFilename(), userId)
        );
        return PrdResponse.from(dto);
    } catch (IOException e) {
        throw new BizException(ErrorCode.PRD_FILE_PARSE_FAILED, "读取上传文件失败");
    }
}
```

**理由**:同步返回 `PrdResponse`,前端用 `FormData` + `fetch` 调即可,无需 SSE 复杂度。

### D7:Service 层流程 — 与 createFromUrl 同质

```java
@Transactional
public PrdDTO createFromFile(CreatePrdFromFileCommand cmd) {
    validateFileSize(cmd.bytes());                          // 大小兜底
    SummarizeResult result = aiService.summarizeFromFile(
        cmd.bytes(), cmd.filename());                        // Tika + AI
    Prd prd = Prd.createFromManual(                          // 直接 DRAFT,不走 INITIALIZING
        result.title(), result.content(), cmd.currentUserId());
    Prd saved = prdRepository.save(prd);
    return toDTO(saved);
}
```

**注意**:不走 `Prd.createFromUrl()` + `INITIALIZING` 流程——file 是同步搞定的,直接 `createFromManual` 落 DRAFT 即可。`sourceUrl` 字段保持 null,这与"URL 路径"的语义区分开。

### D8:前端最小化改动

`app.html` PRD 创建页加一个按钮:
```html
<button onclick="document.getElementById('prd-upload-input').click()">📎 上传文档</button>
<input type="file" id="prd-upload-input" accept=".pdf,.docx,.doc,.txt,.md" style="display:none"
       onchange="uploadPrdFile(this.files[0])">
```

`uploadPrdFile()` 函数用 FormData + fetch(注意:用 `authFetch` 不行,因为 Content-Type 是 multipart,需要直接调 `fetch` 不设 Content-Type)。

成功后跳转到 `app.html#edit?id=<prdId>` 让用户补章节再 submit。

## Risks / Trade-offs

- **[Tika standard 包体积 ~50MB]** → fat jar 变大,启动稍慢。Mitigation:对 PRD 评审系统单实例部署可接受;真要瘦身,后续切回精细 module 组合
- **[Tika XXE / RCE 漏洞历史]** → 跟进 CVE,保持版本最新。Mitigation:固定到当前最新稳定 2.9.x,Spring AI BOM 不冲突即可
- **[扫描件 PDF 返回空文本]** → 用户体验差。Mitigation:Service 检测 `text.isBlank()` 或 `< 10 字符` 抛 `PRD_FILE_PARSE_FAILED`,提示"可能是扫描件,请提供文字版"
- **[`.doc` 旧格式解析慢/兼容性差]** → POI 库历史问题。Mitigation:错误消息引导用户转 `.docx`
- **[内存峰值]** → 10MB 文件 `getBytes()` 直接进堆,并发上传可能 OOM。Mitigation:默认 max-file-size 10MB + 并发上传 PRD 不会高;真要进一步优化用 InputStream 流式处理,后续 change

## Migration Plan

1. **依赖**:parent pom 加 Tika 版本 + dependencyManagement;infrastructure pom 加两个依赖
2. **代码**:domain → infrastructure → application → api → frontend 逐层加,不改既有接口
3. **配置**:application.yml 加 multipart 配置
4. **回滚**:revert commit + 删除 Tika 依赖即可;数据库无变更
5. **验证**:`mvn clean test` 全过 → 启动应用 → 前端上传一个 sample.pdf → 跳到编辑页 → 看 AI 摘要的 title + content
