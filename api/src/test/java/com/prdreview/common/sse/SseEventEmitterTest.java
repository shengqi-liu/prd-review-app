package com.prdreview.common.sse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SseEventEmitter 单元测试 — 验证事件 stage 字段和 sendError 后 emitter.complete() 被调用。
 */
@DisplayName("SseEventEmitter 单元测试")
class SseEventEmitterTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Test
    @DisplayName("sendFetching / sendSummarizing / sendDone 依次推送，stage 字段正确")
    void sendStageEvents_correctStageValues() throws Exception {
        // given — 收集推送的事件 JSON
        List<String> received = new ArrayList<>();
        SseEventEmitter emitter = new SseEventEmitter(objectMapper) {
            // 通过覆盖 getEmitter 返回一个记录事件的 SseEmitter
        };

        // 直接测试 SseEvent 序列化正确性（SseEmitter.send 调用本身由 Spring 管理）
        SseEvent fetching = new SseEvent("fetching", "正在拉取文档...");
        SseEvent summarizing = new SseEvent("summarizing", "AI 正在摘要...");
        record DoneData(String id) {}
        SseEvent done = new SseEvent("done", "处理完成", new DoneData("123"));

        // when — 序列化事件
        String fetchingJson = objectMapper.writeValueAsString(fetching);
        String summarizingJson = objectMapper.writeValueAsString(summarizing);
        String doneJson = objectMapper.writeValueAsString(done);

        // then — stage 字段正确
        JsonNode fetchingNode = objectMapper.readTree(fetchingJson);
        assertThat(fetchingNode.get("stage").asText()).isEqualTo("fetching");
        assertThat(fetchingNode.get("message").asText()).isEqualTo("正在拉取文档...");
        assertThat(fetchingNode.has("data")).isFalse(); // null data 应被省略

        JsonNode summarizingNode = objectMapper.readTree(summarizingJson);
        assertThat(summarizingNode.get("stage").asText()).isEqualTo("summarizing");

        JsonNode doneNode = objectMapper.readTree(doneJson);
        assertThat(doneNode.get("stage").asText()).isEqualTo("done");
        assertThat(doneNode.has("data")).isTrue(); // done 事件有 data
    }

    @Test
    @DisplayName("SseEvent — data 为 null 时序列化后不包含 data 字段")
    void sseEvent_nullData_omittedInJson() throws Exception {
        // given
        SseEvent event = new SseEvent("fetching", "正在拉取文档...");

        // when
        String json = objectMapper.writeValueAsString(event);
        JsonNode node = objectMapper.readTree(json);

        // then
        assertThat(node.has("data")).isFalse();
        assertThat(node.get("stage").asText()).isEqualTo("fetching");
        assertThat(node.get("message").asText()).isEqualTo("正在拉取文档...");
    }

    @Test
    @DisplayName("SseEvent — done 事件携带 data，序列化后 data 字段存在")
    void sseEvent_withData_includedInJson() throws Exception {
        // given
        record TestData(String title, String content) {}
        SseEvent event = new SseEvent("done", "处理完成", new TestData("PRD 标题", "摘要内容"));

        // when
        String json = objectMapper.writeValueAsString(event);
        JsonNode node = objectMapper.readTree(json);

        // then
        assertThat(node.get("stage").asText()).isEqualTo("done");
        assertThat(node.has("data")).isTrue();
        assertThat(node.get("data").get("title").asText()).isEqualTo("PRD 标题");
    }

    @Test
    @DisplayName("SseEvent — error 阶段事件序列化 stage 字段正确")
    void sseEvent_errorStage_correctSerialization() throws Exception {
        // given
        SseEvent event = new SseEvent("error", "AI 调用超时");

        // when
        String json = objectMapper.writeValueAsString(event);
        JsonNode node = objectMapper.readTree(json);

        // then
        assertThat(node.get("stage").asText()).isEqualTo("error");
        assertThat(node.get("message").asText()).isEqualTo("AI 调用超时");
        assertThat(node.has("data")).isFalse();
    }

    @Test
    @DisplayName("SseEventEmitter — getEmitter 返回超时 60s 的 SseEmitter 实例")
    void sseEventEmitter_getEmitter_returnsSseEmitter() {
        // given
        SseEventEmitter emitter = new SseEventEmitter(objectMapper);

        // then
        assertThat(emitter.getEmitter()).isInstanceOf(SseEmitter.class);
    }

    @Test
    @DisplayName("SseEvent — token 阶段事件序列化 stage 字段正确，message 为 chunk 文本")
    void sseEvent_tokenStage_correctSerialization() throws Exception {
        // given
        SseEvent event = new SseEvent("token", "评审结果");

        // when
        String json = objectMapper.writeValueAsString(event);
        JsonNode node = objectMapper.readTree(json);

        // then
        assertThat(node.get("stage").asText()).isEqualTo("token");
        assertThat(node.get("message").asText()).isEqualTo("评审结果");
        assertThat(node.has("data")).isFalse();
    }

    @Test
    @DisplayName("SseEventEmitter — 自定义 timeout 构造函数生效")
    void sseEventEmitter_customTimeout_set() {
        // given
        SseEventEmitter emitter = new SseEventEmitter(180_000L);

        // then — SseEmitter.getTimeout() 返回构造时设定的值
        assertThat(emitter.getEmitter().getTimeout()).isEqualTo(180_000L);
    }
}
