package com.prdreview.arch;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Bounded Context 隔离约束：
 *
 * <p>每个业务上下文（auth / prd / reviewer / knowledgebase / review）的代码
 * MUST NOT 直接引用其它业务上下文包。</p>
 *
 * <p>以下为允许的共享依赖，不视为"其它上下文"：
 * <ul>
 *   <li>{@code common} — 横切工具（Result / ErrorCode / 异常 / 安全）</li>
 *   <li>{@code system} — 启动验证上下文</li>
 *   <li>{@code ai} — 共享 AI 能力（AiService），prd / reviewer 等按接口契约依赖</li>
 *   <li>{@code auth.model.UserRole} — RBAC 角色枚举，作为 {@code @RequireRole} 注解契约被各 Controller 引用</li>
 * </ul>
 *
 * <p>跨上下文业务通信统一走 application 层事件发布订阅 或 api 层契约调用。</p>
 */
@AnalyzeClasses(packages = "com.prdreview", importOptions = ImportOption.DoNotIncludeTests.class)
public class BoundedContextArchTest {

    /** RBAC 角色枚举：作为 @RequireRole 注解契约，允许各上下文引用 */
    private static final String RBAC_ROLE_ENUM = "com.prdreview.auth.model.UserRole";

    @ArchTest
    static final ArchRule auth_should_not_depend_on_other_contexts =
        contextIsolated("auth");

    @ArchTest
    static final ArchRule prd_should_not_depend_on_other_contexts =
        contextIsolated("prd");

    @ArchTest
    static final ArchRule reviewer_should_not_depend_on_other_contexts =
        contextIsolated("reviewer");

    @ArchTest
    static final ArchRule knowledgebase_should_not_depend_on_other_contexts =
        contextIsolated("knowledgebase");

    @ArchTest
    static final ArchRule review_should_not_depend_on_other_contexts =
        contextIsolated("review");

    private static ArchRule contextIsolated(String context) {
        DescribedPredicate<JavaClass> forbiddenTargets =
            resideInAnyPackage(otherContextPackages(context))
                .and(DescribedPredicate.not(nameMatches(RBAC_ROLE_ENUM)));
        return noClasses()
            .that().resideInAPackage("com.prdreview." + context + "..")
            .should().dependOnClassesThat(forbiddenTargets)
            .as(context + " bounded context MUST NOT depend on other contexts");
    }

    private static DescribedPredicate<JavaClass> nameMatches(String fqcn) {
        return new DescribedPredicate<>("name " + fqcn) {
            @Override
            public boolean test(JavaClass javaClass) {
                return javaClass.getFullName().equals(fqcn);
            }
        };
    }

    /**
     * 其它业务上下文包。{@code ai} 作为共享能力不列入（允许依赖）；
     * {@code reviewer} 含其子上下文 {@code reviewer.style}（评审风格）。
     */
    private static String[] otherContextPackages(String self) {
        String[] all = {"auth", "prd", "reviewer", "knowledgebase", "review"};
        return java.util.Arrays.stream(all)
            .filter(c -> !c.equals(self))
            .map(c -> "com.prdreview." + c + "..")
            .toArray(String[]::new);
    }
}
