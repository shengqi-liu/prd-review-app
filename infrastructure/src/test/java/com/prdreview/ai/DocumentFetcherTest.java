package com.prdreview.ai;

import com.prdreview.common.exception.AiServiceException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DocumentFetcher 单元测试 — 使用 MockWebServer 模拟 HTTP 服务端。
 * <p>依赖 okhttp3 mockwebserver（spring-boot-starter-test 的测试 scope 传递依赖）。
 */
@DisplayName("DocumentFetcher 单元测试")
class DocumentFetcherTest {

    private MockWebServer mockWebServer;
    private DocumentFetcher documentFetcher;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        AiProperties props = new AiProperties();
        props.getFetch().setTimeoutConnectMs(3000);
        props.getFetch().setTimeoutReadMs(5000);
        props.getFetch().setUserAgent("TestBot/1.0");

        documentFetcher = new DocumentFetcher(props);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("HTML Content-Type → Jsoup 提取正文，结果不含 HTML 标签")
    void fetchContent_htmlResponse_extractsBodyText() {
        // given
        String html = """
                <html><head><title>Test</title></head>
                <body>
                  <nav>导航菜单</nav>
                  <main><p>这是 PRD 正文内容，描述核心功能。</p></main>
                  <footer>页脚信息</footer>
                </body></html>
                """;
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(html));

        // when
        String result = documentFetcher.fetchContent(mockWebServer.url("/doc").toString());

        // then
        assertThat(result).doesNotContain("<html>", "<body>", "<p>", "<nav>");
        assertThat(result).contains("PRD 正文内容");
    }

    @Test
    @DisplayName("纯文本 Content-Type → 原文直通，不做 HTML 解析")
    void fetchContent_plainTextResponse_passthrough() {
        // given
        String plainText = "# PRD 标题\n\n## 背景\n\n这是纯文本格式的需求文档。";
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .setBody(plainText));

        // when
        String result = documentFetcher.fetchContent(mockWebServer.url("/doc.md").toString());

        // then
        assertThat(result).isEqualTo(plainText);
    }

    @Test
    @DisplayName("非 2xx 状态码 → 抛出 AiServiceException 包含状态码信息")
    void fetchContent_non2xxStatus_throwsAiServiceException() {
        // given
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));

        // when / then
        assertThatThrownBy(() -> documentFetcher.fetchContent(mockWebServer.url("/not-found").toString()))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("服务端返回 500 → 抛出 AiServiceException 包含状态码信息")
    void fetchContent_500Status_throwsAiServiceException() {
        // given
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        // when / then
        assertThatThrownBy(() -> documentFetcher.fetchContent(mockWebServer.url("/error").toString()))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("500");
    }
}
