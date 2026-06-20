package com.prdreview.common.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * SSE 阶段事件推送工具，封装 {@link SseEmitter}。
 * <p>提供类型化阶段方法（fetching / summarizing / done / error），供 PRD 提交接口等使用。
 *
 * <p>使用示例：
 * <pre>
 *     SseEventEmitter emitter = new SseEventEmitter();
 *     // Controller 返回 emitter.getEmitter()
 *
 *     CompletableFuture.runAsync(() -> {
 *         emitter.sendFetching("正在拉取文档...");
 *         String text = documentFetcher.fetchContent(url);
 *         emitter.sendSummarizing("AI 正在摘要...");
 *         SummarizeResult result = aiService.summarizeText(text);
 *         emitter.sendDone(result);
 *     });
 * </pre>
 *
 * <p><b>线程安全：</b>请在单一线程中顺序调用 send* 方法，{@link SseEmitter#send} 非线程安全。
 */
@Slf4j
public class SseEventEmitter {

    private static final long DEFAULT_TIMEOUT_MS = 60_000L;

    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;

    public SseEventEmitter() {
        this(DEFAULT_TIMEOUT_MS);
    }

    /**
     * 自定义超时构造（长输出场景，如评审员试跑 180s）。
     */
    public SseEventEmitter(long timeoutMs) {
        this.emitter = new SseEmitter(timeoutMs);
        this.objectMapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /** 用于注入自定义 ObjectMapper（测试友好） */
    public SseEventEmitter(ObjectMapper objectMapper) {
        this.emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);
        this.objectMapper = objectMapper;
    }

    // ── 类型化推送方法 ────────────────────────────────────────────────────

    /** 推送 stage=fetching 事件（正在拉取文档） */
    public void sendFetching(String message) {
        send(new SseEvent("fetching", message));
    }

    /** 推送 stage=summarizing 事件（AI 正在摘要） */
    public void sendSummarizing(String message) {
        send(new SseEvent("summarizing", message));
    }

    /**
     * 推送 stage=token 事件（流式 LLM 输出 chunk）。
     *
     * @param chunk token 文本片段
     */
    public void sendToken(String chunk) {
        send(new SseEvent("token", chunk));
    }

    /**
     * 推送 stage=done 事件，携带业务数据。
     *
     * @param data 业务结果对象，序列化为 JSON 放入 data 字段
     */
    public void sendDone(Object data) {
        send(new SseEvent("done", "处理完成", data));
    }

    /**
     * 推送 stage=error 事件，并关闭连接。
     *
     * @param message 错误描述
     */
    public void sendError(String message) {
        send(new SseEvent("error", message));
        emitter.complete();
    }

    /** 显式关闭 SSE 连接 */
    public void complete() {
        emitter.complete();
    }

    /**
     * 返回底层 {@link SseEmitter}，供 Controller 返回给 Spring MVC。
     * <pre>
     *     &#64;GetMapping(value = "/submit", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
     *     public SseEmitter submit(...) {
     *         SseEventEmitter emitter = new SseEventEmitter();
     *         // ... async work
     *         return emitter.getEmitter();
     *     }
     * </pre>
     */
    public SseEmitter getEmitter() {
        return emitter;
    }

    // ── 私有方法 ─────────────────────────────────────────────────────────

    private void send(SseEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event()
                    .data(json, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.error("[SSE] 推送事件失败 stage={} error={}", event.stage(), e.getMessage());
        }
    }
}
