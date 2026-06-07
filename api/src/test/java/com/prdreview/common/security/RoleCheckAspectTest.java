package com.prdreview.common.security;

import com.prdreview.auth.model.UserRole;
import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RoleCheckAspect} 单元测试。
 *
 * <p>使用最小 Spring 上下文（仅注册 Aspect + 测试 Bean），不启动完整应用。</p>
 */
@SpringJUnitConfig(RoleCheckAspectTest.TestConfig.class)
class RoleCheckAspectTest {

    @Autowired
    private TestService testService;

    @BeforeEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void cleanupContext() {
        SecurityContextHolder.clearContext();
    }

    // ── 工具方法 ─────────────────────────────────────────────

    private void loginAs(UserRole role) {
        var principal = new AuthenticatedUser(1L, "testuser", role.name());
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ── 测试用例 ─────────────────────────────────────────────

    @Test
    @DisplayName("ADMIN 角色访问 ADMIN 专属接口 → 通过")
    void admin_can_access_admin_only_endpoint() {
        loginAs(UserRole.ADMIN);
        assertThat(testService.adminOnly()).isEqualTo("admin-ok");
    }

    @Test
    @DisplayName("SUBMITTER 角色访问 ADMIN 专属接口 → FORBIDDEN")
    void submitter_cannot_access_admin_only_endpoint() {
        loginAs(UserRole.SUBMITTER);
        assertThatThrownBy(() -> testService.adminOnly())
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("TEAM_MEMBER 角色访问 ADMIN 专属接口 → FORBIDDEN")
    void team_member_cannot_access_admin_only_endpoint() {
        loginAs(UserRole.TEAM_MEMBER);
        assertThatThrownBy(() -> testService.adminOnly())
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("ADMIN 访问多角色接口（ADMIN|TEAM_MEMBER）→ 通过")
    void admin_can_access_multi_role_endpoint() {
        loginAs(UserRole.ADMIN);
        assertThat(testService.adminOrTeamMember()).isEqualTo("multi-ok");
    }

    @Test
    @DisplayName("TEAM_MEMBER 访问多角色接口（ADMIN|TEAM_MEMBER）→ 通过")
    void team_member_can_access_multi_role_endpoint() {
        loginAs(UserRole.TEAM_MEMBER);
        assertThat(testService.adminOrTeamMember()).isEqualTo("multi-ok");
    }

    @Test
    @DisplayName("SUBMITTER 访问多角色接口（ADMIN|TEAM_MEMBER）→ FORBIDDEN")
    void submitter_cannot_access_multi_role_endpoint() {
        loginAs(UserRole.SUBMITTER);
        assertThatThrownBy(() -> testService.adminOrTeamMember())
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("未登录访问受保护接口 → UNAUTHORIZED")
    void unauthenticated_gets_unauthorized() {
        // SecurityContextHolder 为空
        assertThatThrownBy(() -> testService.adminOnly())
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("类级别注解：ADMIN 访问 → 通过")
    void class_level_annotation_allows_admin() {
        loginAs(UserRole.ADMIN);
        assertThat(testService.classLevelMethod()).isEqualTo("class-level-ok");
    }

    @Test
    @DisplayName("类级别注解：SUBMITTER 访问 → FORBIDDEN")
    void class_level_annotation_blocks_submitter() {
        loginAs(UserRole.SUBMITTER);
        assertThatThrownBy(() -> testService.classLevelMethod())
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("方法级注解覆盖类级注解：SUBMITTER 访问允许自身的接口 → 通过")
    void method_annotation_overrides_class_annotation() {
        loginAs(UserRole.SUBMITTER);
        // classLevelAdminService 类级是 ADMIN，但此方法覆盖为允许所有角色
        assertThat(testService.methodOverridesClass()).isEqualTo("method-override-ok");
    }

    // ── 测试配置 ─────────────────────────────────────────────

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {
        @Bean
        RoleCheckAspect roleCheckAspect() { return new RoleCheckAspect(); }

        @Bean
        TestService testService() { return new TestService(); }

        @Bean
        ClassLevelService classLevelService() { return new ClassLevelService(); }
    }

    /** 方法级注解测试桩 */
    static class TestService {
        @RequireRole(UserRole.ADMIN)
        String adminOnly() { return "admin-ok"; }

        @RequireRole({UserRole.ADMIN, UserRole.TEAM_MEMBER})
        String adminOrTeamMember() { return "multi-ok"; }

        // 委托给类级别注解的 Bean
        @Autowired
        ClassLevelService classLevelService;

        String classLevelMethod() { return classLevelService.classLevelMethod(); }

        String methodOverridesClass() { return classLevelService.methodOverridesClass(); }
    }

    /** 类级注解测试桩 */
    @RequireRole(UserRole.ADMIN)
    static class ClassLevelService {
        String classLevelMethod() { return "class-level-ok"; }

        @RequireRole({UserRole.ADMIN, UserRole.TEAM_MEMBER, UserRole.SUBMITTER})
        String methodOverridesClass() { return "method-override-ok"; }
    }
}
