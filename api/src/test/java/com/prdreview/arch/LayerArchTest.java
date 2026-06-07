package com.prdreview.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 分层架构约束：保证各层职责清晰。
 *
 * <p>说明：当前仅扫描 {@code com.prdreview} 根包；后续 change 增加其它上下文后仍生效。</p>
 */
@AnalyzeClasses(packages = "com.prdreview", importOptions = ImportOption.DoNotIncludeTests.class)
public class LayerArchTest {

    /** 领域层不得引用 Spring Web 注解（保证可独立测试、不耦合 Web 框架）。 */
    @ArchTest
    static final ArchRule domain_should_not_depend_on_spring_web =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..");

    /** 领域层不得引用 Spring 持久化注解（避免 ORM 入侵领域模型）。 */
    @ArchTest
    static final ArchRule domain_should_not_depend_on_persistence =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework.data..",
                "jakarta.persistence..");

    /** 应用层不得直接出现 @RestController。 */
    @ArchTest
    static final ArchRule application_should_not_contain_rest_controller =
        noClasses()
            .that().resideInAPackage("..application..")
            .should().beAnnotatedWith("org.springframework.web.bind.annotation.RestController");
}
