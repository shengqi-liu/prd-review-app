package com.prdreview.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prdreview.ai.dto.SummarizeResult;
import com.prdreview.common.exception.AiServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.StreamResponseSpec;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiServiceImpl 单元测试")
class AiServiceImplTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private DocumentFetcher documentFetcher;

    @Mock
    private DocumentParser documentParser;

    @Mock
    private ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec callResponseSpec;

    @Mock
    private StreamResponseSpec streamResponseSpec;

    private AiServiceImpl aiService;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        aiService = new AiServiceImpl(chatClientBuilder, documentFetcher, documentParser, new ObjectMapper());
    }

    @Test
    @DisplayName("summarizeText — LLM 返回合法 JSON，正确解析 title 和 content")
    void summarizeText_validJson_returnsResult() {
        // given
        String rawText = "这是一份关于用户认证系统的产品需求文档，描述了登录、注册和权限管理功能。包含多个用户故事和验收标准。";
        String aiJson = """
                {"title":"用户认证系统 PRD","content":"本文档描述登录、注册和权限管理功能，包含用户故事和验收标准。"}
                """;

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(aiJson);

        // when
        SummarizeResult result = aiService.summarizeText(rawText);

        // then
        assertThat(result.title()).isEqualTo("用户认证系统 PRD");
        assertThat(result.content()).contains("登录");
        assertThat(result.content()).contains("注册");
    }

    @Test
    @DisplayName("summarizeText — LLM 返回带前缀说明文字的 JSON，依然能正确解析")
    void summarizeText_jsonWithPrefix_returnsResult() {
        // given
        String rawText = "产品需求文档内容示例，包含足够的文字以触发摘要逻辑，内容描述某个系统功能模块的详细需求。";
        String aiResponse = """
                好的，以下是摘要：
                {"title":"系统功能模块 PRD","content":"本文档描述系统功能模块详细需求。"}
                希望对你有帮助。
                """;

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(aiResponse);

        // when
        SummarizeResult result = aiService.summarizeText(rawText);

        // then
        assertThat(result.title()).isEqualTo("系统功能模块 PRD");
        assertThat(result.content()).isNotBlank();
    }

    @Test
    @DisplayName("summarizeText — LLM 返回非 JSON，触发回退策略")
    void summarizeText_nonJson_fallback() {
        // given
        String rawText = "这是产品需求文档的正文内容，包含足够的文字用于测试回退逻辑处理非法 JSON 的情况。功能描述：用户可以创建账户。";
        String nonJsonResponse = "无法处理该请求，请稍后重试。";

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(nonJsonResponse);

        // when
        SummarizeResult result = aiService.summarizeText(rawText);

        // then — 回退策略：title 取前 20 字，content 取前 500 字
        assertThat(result.title()).isNotBlank();
        assertThat(result.content()).isNotBlank();
        assertThat(result.content().length()).isLessThanOrEqualTo(503); // 500 + "..."
    }

    @Test
    @DisplayName("summarizeFromUrl — 调用 documentFetcher 拉取内容后调用 summarizeText")
    void summarizeFromUrl_callsFetcherThenSummarize() {
        // given
        String url = "https://internal.wiki/prd-doc";
        String fetchedText = "从 URL 拉取的文档内容，描述了产品需求的核心功能和用户故事，包含多个章节。";
        String aiJson = """
                {"title":"内部 Wiki PRD","content":"产品核心功能和用户故事摘要。"}
                """;

        when(documentFetcher.fetchContent(url)).thenReturn(fetchedText);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(aiJson);

        // when
        SummarizeResult result = aiService.summarizeFromUrl(url);

        // then
        assertThat(result.title()).isEqualTo("内部 Wiki PRD");
        verify(documentFetcher).fetchContent(url);
    }

    @Test
    @DisplayName("summarizeFromUrl — documentFetcher 抛出异常时向上传播")
    void summarizeFromUrl_fetcherThrows_propagatesException() {
        // given
        String url = "https://internal.wiki/prd-doc";
        when(documentFetcher.fetchContent(url))
                .thenThrow(new AiServiceException("URL 读取超时: " + url));

        // when / then
        assertThatThrownBy(() -> aiService.summarizeFromUrl(url))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("URL 读取超时");
    }

    // ── streamCompletion（#9） ────────────────────────────────────────

    @Test
    @DisplayName("streamCompletion — 正常逐 token 返回 Flux")
    void streamCompletion_returnsFlux() {
        // given
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("评", "审", "结", "果"));

        // when / then
        StepVerifier.create(aiService.streamCompletion("测试 prompt"))
                .expectNext("评")
                .expectNext("审")
                .expectNext("结")
                .expectNext("果")
                .verifyComplete();
    }

    @Test
    @DisplayName("streamCompletion — 底层异常被包装为 AiServiceException 通过 Flux onError 传播")
    void streamCompletion_errorWrapped() {
        // given
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content())
                .thenReturn(Flux.error(new RuntimeException("网络异常")));

        // when / then
        StepVerifier.create(aiService.streamCompletion("测试 prompt"))
                .expectErrorMatches(ex -> ex instanceof AiServiceException
                        && ex.getMessage().contains("AI 流式调用失败")
                        && ex.getMessage().contains("网络异常"))
                .verify();
    }

    @Test
    @DisplayName("streamCompletion — 底层已是 AiServiceException 时不重复包装")
    void streamCompletion_aiServiceExceptionPassthrough() {
        // given
        AiServiceException original = new AiServiceException("原始错误");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.error(original));

        // when / then
        StepVerifier.create(aiService.streamCompletion("测试 prompt"))
                .expectErrorMatches(ex -> ex == original) // 同一实例
                .verify();
    }

    // ──────────────────────────────────────────────────
    // #7 summarizeFromFile
    // ──────────────────────────────────────────────────

    @Test
    @DisplayName("summarizeFromFile — 调 DocumentParser → 复用 summarizeText 返回 SummarizeResult")
    void summarizeFromFile_parsesAndSummarizes() {
        byte[] bytes = "fake pdf bytes".getBytes();
        String filename = "test.pdf";
        String parsedText = "这是从 PDF 解析出的纯文本内容,讲述了一个产品评审系统的需求。";

        when(documentParser.parseText(bytes, filename)).thenReturn(parsedText);
        // summarizeText 走标准 chat client 链路返回合法 JSON
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(
            "{\"title\":\"测试文档\",\"content\":\"摘要正文 100 字以内描述...\"}"
        );

        com.prdreview.ai.dto.SummarizeResult result = aiService.summarizeFromFile(bytes, filename);

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("测试文档");
        assertThat(result.content()).contains("摘要正文");
        verify(documentParser).parseText(bytes, filename);
    }

    @Test
    @DisplayName("summarizeFromFile — DocumentParser 抛 BizException 时向上传播,不调 ChatClient")
    void summarizeFromFile_parserFails_propagates() {
        byte[] bytes = "fake zip".getBytes();
        com.prdreview.common.exception.BizException parserErr =
            new com.prdreview.common.exception.BizException(
                com.prdreview.common.exception.ErrorCode.PRD_FILE_TYPE_UNSUPPORTED,
                "不支持: application/zip");
        when(documentParser.parseText(bytes, "x.zip")).thenThrow(parserErr);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            aiService.summarizeFromFile(bytes, "x.zip"))
            .isSameAs(parserErr);

        // 不应触达 chat client
        org.mockito.Mockito.verify(chatClient, org.mockito.Mockito.never()).prompt();
    }
}
