package com.prdreview.common.sse;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SSE 阶段事件 DTO。序列化为：
 * <pre>
 * {"stage":"fetching","message":"正在拉取文档..."}
 * {"stage":"done","message":"处理完成","data":{...}}
 * </pre>
 * {@code data} 为 null 时自动省略（依赖 {@link JsonInclude.Include#NON_NULL}）。
 *
 * @param stage   阶段标识（fetching / summarizing / done / error）
 * @param message 人类可读描述
 * @param data    附加数据（done 事件携带业务结果，其他阶段为 null）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SseEvent(String stage, String message, Object data) {

    /** 构造不含 data 的阶段事件 */
    public SseEvent(String stage, String message) {
        this(stage, message, null);
    }
}
