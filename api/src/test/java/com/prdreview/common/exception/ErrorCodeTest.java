package com.prdreview.common.exception;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void errorCode_values_should_be_unique() {
        ErrorCode[] values = ErrorCode.values();

        Map<Integer, List<ErrorCode>> grouped = Arrays.stream(values)
            .collect(Collectors.groupingBy(ErrorCode::getCode));

        List<Integer> duplicates = grouped.entrySet().stream()
            .filter(e -> e.getValue().size() > 1)
            .map(Map.Entry::getKey)
            .toList();

        assertThat(duplicates)
            .as("ErrorCode 存在重复数值: %s", duplicates)
            .isEmpty();
    }

    @Test
    void errorCode_should_have_non_empty_message() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.getMessage())
                .as("ErrorCode.%s 的 message 不能为空", code.name())
                .isNotBlank();
        }
    }

    @Test
    void bizException_should_carry_error_code() {
        BizException ex = new BizException(ErrorCode.PRD_NOT_FOUND);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PRD_NOT_FOUND);
        assertThat(ex.getCode()).isEqualTo(30001);
        assertThat(ex.getMessage()).isEqualTo("PRD 方案不存在");
    }

    @Test
    void bizException_should_allow_custom_message() {
        BizException ex = new BizException(ErrorCode.PARAM_INVALID, "字段 title 不能为空");
        assertThat(ex.getCode()).isEqualTo(10002);
        assertThat(ex.getMessage()).isEqualTo("字段 title 不能为空");
    }
}
