package com.prdreview.system.controller;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 异常触发测试端点，仅在 {@code dev} profile 下注册为 Bean。
 *
 * <p>用于联调验证全局异常处理器，生产环境不注册、不暴露。</p>
 */
@Profile("dev")
@Tag(name = "系统", description = "系统健康探针")
@RestController
@RequestMapping("/api/v1/ping")
public class DevPingController {

    @Operation(summary = "触发测试异常（dev only）")
    @GetMapping("/error/{type}")
    public String triggerError(@PathVariable String type) {
        switch (type) {
            case "biz"    -> throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "测试：资源不存在");
            case "param"  -> throw new BizException(ErrorCode.PARAM_INVALID, "测试：参数不合法");
            case "system" -> throw new RuntimeException("测试：系统未捕获异常");
            default       -> throw new BizException(ErrorCode.PARAM_INVALID, "type 参数无效，可选：biz / param / system");
        }
    }
}
