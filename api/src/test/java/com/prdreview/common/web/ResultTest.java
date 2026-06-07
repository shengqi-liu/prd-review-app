package com.prdreview.common.web;

import com.prdreview.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class ResultTest {

    private static final String TRACE_ID = "test-trace-id-001";

    @BeforeEach
    void setUp() {
        MDC.put("traceId", TRACE_ID);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void success_with_data_should_return_code_0() {
        Result<String> result = Result.success("hello");
        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getMessage()).isEqualTo("success");
        assertThat(result.getData()).isEqualTo("hello");
        assertThat(result.getTraceId()).isEqualTo(TRACE_ID);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void success_without_data_should_return_null_data() {
        Result<Void> result = Result.success();
        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isNull();
    }

    @Test
    void error_with_errorCode_should_return_correct_code() {
        Result<Object> result = Result.error(ErrorCode.PRD_NOT_FOUND);
        assertThat(result.getCode()).isEqualTo(30001);
        assertThat(result.getMessage()).isEqualTo("PRD 方案不存在");
        assertThat(result.getData()).isNull();
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void error_with_custom_message_should_override_default() {
        Result<Object> result = Result.error(ErrorCode.PARAM_INVALID, "字段 title 不能为空");
        assertThat(result.getCode()).isEqualTo(10002);
        assertThat(result.getMessage()).isEqualTo("字段 title 不能为空");
    }

    @Test
    void result_should_carry_traceId_from_mdc() {
        Result<String> result = Result.success("data");
        assertThat(result.getTraceId()).isEqualTo(TRACE_ID);
    }

    @Test
    void result_traceId_should_be_null_when_mdc_empty() {
        MDC.clear();
        Result<String> result = Result.success("data");
        assertThat(result.getTraceId()).isNull();
    }
}
