package com.prdreview.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AiProperties 配置绑定验证 — 轻量切片测试，只加载 ConfigurationProperties，不启动完整 Spring 容器。
 * <p>验证 application.yml 中 ai.fetch.* 默认值被正确绑定。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
@EnableConfigurationProperties(AiProperties.class)
@DisplayName("AiProperties 配置绑定验证")
class AiPropertiesTest {

    @Autowired
    private AiProperties aiProperties;

    @Test
    @DisplayName("ai.fetch.timeout-connect-ms 默认值为 5000")
    void fetch_timeoutConnectMs_defaultIs5000() {
        assertThat(aiProperties.getFetch().getTimeoutConnectMs()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("ai.fetch.timeout-read-ms 默认值为 15000")
    void fetch_timeoutReadMs_defaultIs15000() {
        assertThat(aiProperties.getFetch().getTimeoutReadMs()).isEqualTo(15000L);
    }

    @Test
    @DisplayName("ai.fetch.user-agent 默认值为 PrdReview-Bot/1.0")
    void fetch_userAgent_defaultIsPrdReviewBot() {
        assertThat(aiProperties.getFetch().getUserAgent()).isEqualTo("PrdReview-Bot/1.0");
    }
}
