package com.prdreview.ai;

import com.prdreview.common.exception.AiServiceException;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * 服务端文档拉取工具：通过 WebClient 读取 URL 内容，根据 Content-Type 决定是否调用 Jsoup 提取正文。
 * <ul>
 *   <li>HTML 内容 → Jsoup 提取 body 正文，去除脚本/样式标签</li>
 *   <li>纯文本 / Markdown → 直接返回原始字符串</li>
 *   <li>连接超时 / 响应超时 → 抛出 {@link AiServiceException}</li>
 *   <li>非 2xx 状态码 → 抛出 {@link AiServiceException}</li>
 * </ul>
 */
@Slf4j
@Component
public class DocumentFetcher {

    private final WebClient webClient;

    public DocumentFetcher(AiProperties props) {
        AiProperties.Fetch fetch = props.getFetch();

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) fetch.getTimeoutConnectMs())
                .responseTimeout(Duration.ofMillis(fetch.getTimeoutReadMs()));

        this.webClient = WebClient.builder()
                .defaultHeader("User-Agent", fetch.getUserAgent())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * 拉取 URL 内容，返回提取后的纯文本。
     *
     * @param url 目标 URL
     * @return 文档正文（纯文本）
     * @throws AiServiceException 连接超时、响应超时或非 2xx 时抛出
     */
    public String fetchContent(String url) {
        log.info("[AI] 开始拉取文档 url={}", url);
        try {
            ResponseEntity<String> response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .toEntity(String.class)
                    .block();

            if (response == null) {
                throw new AiServiceException("URL 返回空响应: " + url);
            }

            HttpStatusCode status = response.getStatusCode();
            if (!status.is2xxSuccessful()) {
                throw new AiServiceException("URL 返回非 2xx 状态码: " + status.value() + ", url=" + url);
            }

            String body = response.getBody();
            if (body == null || body.isBlank()) {
                throw new AiServiceException("URL 返回空内容: " + url);
            }

            String contentType = response.getHeaders().getContentType() != null
                    ? response.getHeaders().getContentType().toString()
                    : "";

            if (contentType.contains("text/html")) {
                String text = Jsoup.parse(body).body().text();
                log.debug("[AI] HTML 正文提取完成 url={} chars={}", url, text.length());
                return text;
            }

            log.debug("[AI] 纯文本直通 url={} chars={}", url, body.length());
            return body;

        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.toLowerCase().contains("timeout") || msg.toLowerCase().contains("connection")) {
                throw new AiServiceException("URL 读取超时: " + url, e);
            }
            throw new AiServiceException("URL 读取失败: " + url + " — " + msg, e);
        }
    }
}
