package com.prdreview.common.security;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户上下文工具。
 *
 * <p>从 SecurityContextHolder 获取已认证用户信息。未登录时调用抛出 {@link BizException}。</p>
 */
public class CurrentUser {

    private CurrentUser() {}

    public static Long getCurrentUserId() {
        return getPrincipal().getId();
    }

    public static String getCurrentUsername() {
        return getPrincipal().getUsername();
    }

    public static String getCurrentUserRole() {
        return getPrincipal().getRole();
    }

    private static AuthenticatedUser getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AuthenticatedUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return (AuthenticatedUser) auth.getPrincipal();
    }
}
