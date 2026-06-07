package com.prdreview.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Bounded Context 隔离约束：
 *
 * <p>每个上下文（auth / prd / reviewer / reviewstyle / knowledgebase / review）的代码
 * MUST NOT 直接引用其它上下文包；横切上下文 {@code common} 与启动验证上下文 {@code system} 除外。</p>
 *
 * <p>跨上下文通信统一走 application 层事件发布订阅 或 api 层契约调用。</p>
 */
@AnalyzeClasses(packages = "com.prdreview", importOptions = ImportOption.DoNotIncludeTests.class)
public class BoundedContextArchTest {

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
    static final ArchRule reviewstyle_should_not_depend_on_other_contexts =
        contextIsolated("reviewstyle");

    @ArchTest
    static final ArchRule knowledgebase_should_not_depend_on_other_contexts =
        contextIsolated("knowledgebase");

    @ArchTest
    static final ArchRule review_should_not_depend_on_other_contexts =
        contextIsolated("review");

    private static ArchRule contextIsolated(String context) {
        return noClasses()
            .that().resideInAPackage("com.prdreview." + context + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                otherContextPackages(context))
            .as(context + " bounded context MUST NOT depend on other contexts");
    }

    private static String[] otherContextPackages(String self) {
        String[] all = {"auth", "prd", "reviewer", "reviewstyle", "knowledgebase", "review"};
        return java.util.Arrays.stream(all)
            .filter(c -> !c.equals(self))
            .map(c -> "com.prdreview." + c + "..")
            .toArray(String[]::new);
    }
}
