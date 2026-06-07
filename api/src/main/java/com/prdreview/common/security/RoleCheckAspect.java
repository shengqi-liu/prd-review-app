package com.prdreview.common.security;

import com.prdreview.auth.model.UserRole;
import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 角色权限 AOP 拦截器。
 *
 * <p>拦截所有标注了 {@link RequireRole} 的方法或类，
 * 从 {@link CurrentUser} 获取当前登录用户角色，校验是否在允许列表内。</p>
 *
 * <p>优先级：方法级注解 > 类级注解。</p>
 *
 * <p>未登录时 {@link CurrentUser} 抛 UNAUTHORIZED；
 * 角色不符时本类抛 FORBIDDEN(20002)，由 GlobalExceptionHandler 统一包装输出。</p>
 */
@Slf4j
@Aspect
@Component
public class RoleCheckAspect {

    /**
     * 拦截所有标注了 {@code @RequireRole} 的方法（包括通过类注解继承的情况）。
     */
    @Before("@within(com.prdreview.common.security.RequireRole) || @annotation(com.prdreview.common.security.RequireRole)")
    public void checkRole(JoinPoint joinPoint) {
        RequireRole annotation = resolveAnnotation(joinPoint);
        if (annotation == null) {
            return;
        }

        // CurrentUser 未登录时自动抛 UNAUTHORIZED
        String currentRole = CurrentUser.getCurrentUserRole();

        UserRole[] allowed = annotation.value();
        boolean permitted = Arrays.stream(allowed)
                .anyMatch(role -> role.name().equals(currentRole));

        if (!permitted) {
            log.warn("[RBAC] 角色不足: currentRole={}, required={}, method={}",
                    currentRole, Arrays.toString(allowed),
                    joinPoint.getSignature().toShortString());
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * 解析注解：方法级优先，其次类级。
     */
    private RequireRole resolveAnnotation(JoinPoint joinPoint) {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        Method method = sig.getMethod();

        // 方法级注解优先
        RequireRole ann = AnnotationUtils.findAnnotation(method, RequireRole.class);
        if (ann != null) {
            return ann;
        }
        // 回退到类级注解
        return AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), RequireRole.class);
    }
}
