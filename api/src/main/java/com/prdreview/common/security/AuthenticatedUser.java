package com.prdreview.common.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 已认证用户信息，存放于 SecurityContextHolder 的 principal。
 */
@Getter
@AllArgsConstructor
public class AuthenticatedUser {
    private final Long id;
    private final String username;
    private final String role;
}
