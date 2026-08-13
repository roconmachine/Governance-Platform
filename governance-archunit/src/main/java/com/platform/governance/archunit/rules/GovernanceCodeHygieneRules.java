package com.platform.governance.archunit.rules;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * General hygiene rules that aren't specific to any one governance module,
 * but are cheap wins worth enforcing fleet-wide alongside the others.
 */
public final class GovernanceCodeHygieneRules {

    private GovernanceCodeHygieneRules() {
    }

    /** Constructor injection only - no @Autowired fields, which are harder to test and hide required dependencies. */
    public static ArchRule constructorInjectionOnly(String basePackage) {
        return noFields()
                .that().areDeclaredInClassesThat().resideInAPackage(basePackage + "..")
                .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                .because("field injection hides required dependencies and is harder to unit test " +
                        "than constructor injection - use a constructor instead");
    }

    /** No System.out/System.err in service code - use SLF4J so log output actually respects the platform's logging/masking pipeline. */
    public static ArchRule noStandardStreamUsage(String basePackage) {
        return noClasses()
                .that().resideInAPackage(basePackage + "..")
                .should().accessField(System.class, "out")
                .orShould().accessField(System.class, "err")
                .because("System.out/err bypasses the structured logging pipeline entirely - " +
                        "including correlation-id/actor MDC context and @Sensitive masking - use SLF4J instead");
    }
}
