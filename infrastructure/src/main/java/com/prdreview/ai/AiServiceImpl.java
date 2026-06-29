package com.prdreview.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prdreview.ai.cache.LlmCacheKeys;
import com.prdreview.ai.cache.LlmCacheService;
import com.prdreview.ai.dto.SummarizeResult;
import com.prdreview.ai.service.AiService;
import com.prdreview.common.exception.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Optional;
import java.util.concurrent.TimeoutException;

/**
 * {@link AiService} 的 Spring AI 实现。
 * <p>注入 Spring AI {@link ChatClient.Builder}（由激活的 provider starter 自动配置），
 * 在构造时 build 出 {@link ChatClient}。切换 provider 只需替换 starter 依赖 + profile YAML。
 */
@Slf4j
@Service
public class AiServiceImpl implements AiService {

    private static final int LLM_TIMEOUT_SECONDS = 30;
    private static final int FALLBACK_CONTENT_LENGTH = 500;
    /** 缓存 key 的 provider 标识(我们走 OpenAI 兼容协议指向 DeepSeek)。 */
    private static final String LLM_PROVIDER = "openai-compatible";

    private final ChatClient chatClient;
    private final DocumentFetcher documentFetcher;
    private final DocumentParser documentParser;
    private final ObjectMapper objectMapper;
    /** LLM 响应缓存(可选);测试场景注入 null 也能正常跑。 */
    private final LlmCacheService llmCacheService;
    private final String llmModel;

    @Autowired
    public AiServiceImpl(ChatClient.Builder chatClientBuilder,
                         DocumentFetcher documentFetcher,
                         DocumentParser documentParser,
                         ObjectMapper objectMapper,
                         @org.springframework.beans.factory.annotation.Autowired(required = false)
                         LlmCacheService llmCacheService,
                         @Value("${spring.ai.openai.chat.options.model:unknown}") String llmModel) {
        this.chatClient = chatClientBuilder.build();
        this.documentFetcher = documentFetcher;
        this.documentParser = documentParser;
        this.objectMapper = objectMapper;
        this.llmCacheService = llmCacheService;
        this.llmModel = llmModel;
    }

    @Override
    public SummarizeResult summarizeFromUrl(String url) {
        log.info("[AI] summarizeFromUrl url={}", url);
        String rawText = documentFetcher.fetchContent(url);
        SummarizeResult result = summarizeText(rawText);
        log.info("[AI] summarizeFromUrl 完成 url={} title={}", url, result.title());
        return result;
    }

    @Override
    public SummarizeResult summarizeFromFile(byte[] bytes, String filename) {
        log.info("[AI] summarizeFromFile filename={} bytes={}", filename, bytes == null ? 0 : bytes.length);
        // DocumentParser 已处理 MIME 白名单 + 解析失败 + 文本过短的所有异常
        String rawText = documentParser.parseText(bytes, filename);
        SummarizeResult result = summarizeText(rawText);
        log.info("[AI] summarizeFromFile 完成 filename={} title={}", filename, result.title());
        return result;
    }

    @Override
    public SummarizeResult summarizeText(String rawText) {
        log.info("[AI] summarizeText chars={}", rawText == null ? 0 : rawText.length());

        // 1) 缓存查询(失败降级)
        String cacheKey = LlmCacheKeys.compute(LLM_PROVIDER, llmModel, rawText);
        if (llmCacheService != null) {
            Optional<String> cached = llmCacheService.get(cacheKey);
            if (cached.isPresent()) {
                try {
                    return objectMapper.readValue(cached.get(), SummarizeResult.class);
                } catch (Exception ex) {
                    // 反序列化失败(老格式数据)→ 当作 miss,继续走 LLM
                    log.warn("[AI] 缓存反序列化失败 key={} err={}",
                        cacheKey.substring(0, 8), ex.getMessage());
                }
            }
        }

        // 2) 真调 LLM
        String prompt = buildPrompt(rawText);
        String aiResponse;
        try {
            aiResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            if (e instanceof TimeoutException || (e.getCause() instanceof TimeoutException)) {
                throw new AiServiceException("AI 调用超时（" + LLM_TIMEOUT_SECONDS + "s）", e);
            }
            throw new AiServiceException("AI 调用失败: " + e.getMessage(), e);
        }

        // 3) 解析:成功才写缓存,fallback 不写
        Optional<SummarizeResult> parsed = parseResultIfValid(aiResponse);
        SummarizeResult result = parsed.orElseGet(() -> fallback(rawText));
        if (parsed.isPresent() && llmCacheService != null) {
            try {
                String json = objectMapper.writeValueAsString(result);
                int tokenEstimate = rawText == null ? 0 : rawText.length() / 4;
                llmCacheService.put(cacheKey, json, LLM_PROVIDER, llmModel,
                    LlmCacheKeys.preview(rawText), tokenEstimate);
            } catch (Exception ex) {
                log.warn("[AI] 缓存写入失败,不影响响应: {}", ex.getMessage());
            }
        }
        return result;
    }

    @Override
    public Flux<String> streamCompletion(String prompt) {
        log.info("[AI] streamCompletion chars={}", prompt.length());
        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .onErrorMap(this::mapStreamError);
    }

    @Override
    public Flux<String> streamCompletion(String systemPrompt, String userMessage) {
        log.info("[AI] streamCompletion system={} user={}", systemPrompt.length(), userMessage.length());
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .stream()
                .content()
                .onErrorMap(this::mapStreamError);
    }

    // ── 私有方法 ─────────────────────────────────────────────────────────

    private Throwable mapStreamError(Throwable ex) {
        if (ex instanceof AiServiceException ase) {
            return ase;
        }
        return new AiServiceException("AI 流式调用失败: " + ex.getMessage(), ex);
    }

    private String buildPrompt(String rawText) {
        // 控制传入 token 量，超长文档截取前 8000 字
        String truncated = rawText.length() > 8000 ? rawText.substring(0, 8000) + "..." : rawText;
        return """
                请从以下文档内容中提取摘要，严格按照 JSON 格式输出，不要包含任何多余说明：

                {"title":"文档标题（简明扼要，20字以内）","content":"核心内容摘要（100-500字，包含文档主要目的、核心功能和关键要点）"}

                文档内容：
                """ + truncated;
    }

    /**
     * 尝试解析 AI 响应为合法 SummarizeResult,失败返回 empty(供上层决定是否走 fallback)。
     * 合法 = JSON 含 title + content 且都非空。
     */
    private Optional<SummarizeResult> parseResultIfValid(String aiResponse) {
        if (aiResponse == null || aiResponse.isBlank()) {
            log.warn("[AI] AI 返回空响应");
            return Optional.empty();
        }
        String json = extractJson(aiResponse);
        try {
            JsonNode node = objectMapper.readTree(json);
            String title = node.path("title").asText(null);
            String content = node.path("content").asText(null);
            if (title != null && !title.isBlank() && content != null && !content.isBlank()) {
                return Optional.of(new SummarizeResult(title.trim(), content.trim()));
            }
        } catch (JsonProcessingException e) {
            log.warn("[AI] JSON 解析失败 response={}", aiResponse.substring(0, Math.min(200, aiResponse.length())));
        }
        log.warn("[AI] AI 响应非预期 JSON,将触发 fallback");
        return Optional.empty();
    }

    /**
     * 从 AI 响应中提取第一个 JSON 对象（应对 AI 在 JSON 前后添加说明文字的情况）。
     */
    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    /**
     * 回退策略：title 取 URL 域名（若无则取前 20 字），content 取原文前 500 字。
     */
    private SummarizeResult fallback(String rawText) {
        String title = rawText.length() > 20
                ? rawText.substring(0, 20).trim() + "..."
                : rawText.trim();
        String content = rawText.length() > FALLBACK_CONTENT_LENGTH
                ? rawText.substring(0, FALLBACK_CONTENT_LENGTH) + "..."
                : rawText;
        return new SummarizeResult(title, content);
    }
}
