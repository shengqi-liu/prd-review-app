package com.prdreview.auth.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 登录结果。
 */
@Data
@Builder
public class LoginResult {
    private String accessToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private long expiresIn;
}
