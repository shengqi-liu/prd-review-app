package com.prdreview.prd.controller;

import com.prdreview.common.security.CurrentUser;
import com.prdreview.common.sse.SseEventEmitter;
import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import com.prdreview.prd.CreatePrdCommand;
import com.prdreview.prd.CreatePrdFromFileCommand;
import com.prdreview.prd.CreatePrdFromUrlCommand;
import com.prdreview.prd.PrdDTO;
import com.prdreview.prd.PrdPageResult;
import com.prdreview.prd.PrdQueryCommand;
import com.prdreview.prd.UpdatePrdCommand;
import com.prdreview.prd.dto.CreatePrdFromUrlRequest;
import com.prdreview.prd.dto.CreatePrdRequest;
import com.prdreview.prd.dto.PrdResponse;
import com.prdreview.prd.dto.UpdatePrdRequest;
import com.prdreview.prd.service.PrdApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * PRD 资源 Controller。
 *
 * <p>所有接口需登录（JWT 拦截器保证）；权限细节由 ApplicationService 校验。
 */
@Slf4j
@Tag(name = "PRD", description = "PRD 方案管理")
@RestController
@RequestMapping("/api/v1/prds")
@RequiredArgsConstructor
public class PrdController {

    private final PrdApplicationService prdService;

    // ── 5.3 手动创建草稿 ─────────────────────────────────────────────

    @Operation(summary = "创建 PRD 草稿（手动填写）")
    @PostMapping
    public PrdResponse create(@Valid @RequestBody CreatePrdRequest req) {
        Long userId = CurrentUser.getCurrentUserId();
        PrdDTO dto = prdService.createManual(
            new CreatePrdCommand(req.title(), req.content(), userId)
        );
        return PrdResponse.from(dto);
    }

    // ── 5.4 从 URL 创建（SSE 流式） ──────────────────────────────────

    @Operation(summary = "从 URL 创建 PRD（AI 摘要，SSE 流式）")
    @PostMapping(value = "/from-url", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter createFromUrl(@Valid @RequestBody CreatePrdFromUrlRequest req) {
        Long userId = CurrentUser.getCurrentUserId();

        Long prdId = prdService.createFromUrl(
            new CreatePrdFromUrlCommand(req.sourceUrl(), userId)
        );

        SseEventEmitter sseEmitter = new SseEventEmitter();

        CompletableFuture.runAsync(() -> {
            try {
                sseEmitter.sendFetching("正在读取文档...");
                // AiService 内部 summarizeFromUrl = fetchContent + summarizeText
                sseEmitter.sendSummarizing("AI 正在分析内容...");
                var result = prdService.completeInitializationFromUrl(prdId, req.sourceUrl());
                sseEmitter.sendDone(PrdResponse.from(result));
            } catch (Exception e) {
                log.error("PRD id={} AI 初始化失败", prdId, e);
                sseEmitter.sendError(e.getMessage());
            }
        });

        return sseEmitter.getEmitter();
    }

    // ── 5.4b 从文件创建（同步：Tika 解析 + AI 摘要 → DRAFT） ─────────

    @Operation(summary = "从文件创建 PRD（PDF/Word/Markdown/纯文本，≤10MB）")
    @PostMapping(value = "/from-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PrdResponse createFromFile(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "上传文件为空");
        }
        Long userId = CurrentUser.getCurrentUserId();
        try {
            byte[] bytes = file.getBytes();
            PrdDTO dto = prdService.createFromFile(
                new CreatePrdFromFileCommand(bytes, file.getOriginalFilename(), userId)
            );
            return PrdResponse.from(dto);
        } catch (IOException e) {
            throw new BizException(ErrorCode.PRD_FILE_PARSE_FAILED, "读取上传文件失败: " + e.getMessage());
        }
    }

    // ── 5.5 查询详情 ──────────────────────────────────────────────────

    @Operation(summary = "查询 PRD 详情")
    @GetMapping("/{id}")
    public PrdResponse getById(@PathVariable Long id) {
        Long userId = CurrentUser.getCurrentUserId();
        String role = CurrentUser.getCurrentUserRole();
        PrdDTO dto = prdService.getById(id, userId, role);
        return PrdResponse.from(dto);
    }

    // ── 5.6 分页列表 ──────────────────────────────────────────────────

    @Operation(summary = "分页查询 PRD 列表")
    @GetMapping
    public PageResponse list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = CurrentUser.getCurrentUserId();
        String role = CurrentUser.getCurrentUserRole();
        PrdPageResult result = prdService.listPrds(
            new PrdQueryCommand(page, size, userId, role)
        );
        List<PrdResponse> items = result.items().stream()
            .map(PrdResponse::from)
            .toList();
        return new PageResponse(result.total(), items);
    }

    // ── 5.7 更新草稿 ──────────────────────────────────────────────────

    @Operation(summary = "更新 PRD 草稿（仅 DRAFT 状态）")
    @PutMapping("/{id}")
    public PrdResponse update(@PathVariable Long id, @Valid @RequestBody UpdatePrdRequest req) {
        Long userId = CurrentUser.getCurrentUserId();
        PrdDTO dto = prdService.updateDraft(
            new UpdatePrdCommand(id, req.title(), req.content(), req.version(), userId)
        );
        return PrdResponse.from(dto);
    }

    // ── 5.8 软删除 ────────────────────────────────────────────────────

    @Operation(summary = "删除 PRD（逻辑删除，仅 DRAFT/INITIALIZING 状态）")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        Long userId = CurrentUser.getCurrentUserId();
        String role = CurrentUser.getCurrentUserRole();
        prdService.softDelete(id, userId, role);
    }

    // ── 5.9 提交评审 ──────────────────────────────────────────────────

    @Operation(summary = "提交 PRD 评审（DRAFT → SUBMITTED）")
    @PostMapping("/{id}/submit")
    public PrdResponse submit(@PathVariable Long id) {
        Long userId = CurrentUser.getCurrentUserId();
        PrdDTO dto = prdService.submitPrd(id, userId);
        return PrdResponse.from(dto);
    }

    // ── 内部响应类 ────────────────────────────────────────────────────

    public record PageResponse(long total, List<PrdResponse> items) {}
}
