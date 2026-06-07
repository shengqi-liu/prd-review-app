package com.prdreview.system.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统健康探针。属于 {@code system} 上下文，用于骨架验证。所有环境均可用。
 */
@Tag(name = "系统", description = "系统健康探针")
@RestController
@RequestMapping("/api/v1/ping")
public class PingController {

    @Operation(summary = "健康探针", description = "返回 pong，用于验证服务可用性")
    @GetMapping
    public String ping() {
        return "pong";
    }
}
