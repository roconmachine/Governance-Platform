package com.platform.governance.archunit.rules;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Enforces that governance annotations from OTHER modules are actually
 * applied where policy says they must be - e.g. a business-facing @Service
 * method can't silently lose its @Auditable annotation in a refactor without
 * a CI failure catching it.
 *
 * Deliberately referenced by fully-qualified annotation NAME (a String), not
 * by importing governance-audit's Auditable class directly - the
 * same isolation principle used throughout this platform: this module has
 * zero compile dependency on any other governance module, so a service
 * that only uses governance-archunit (without governance-audit)
 * still builds fine; this specific rule would just never find a match to
 * violate, which ArchUnit treats as vacuously true rather than an error.
 */
public final class GovernanceAnnotationRules {

    private static final String AUDITABLE_ANNOTATION = "com.platform.governance.audit.annotation.Auditable";

    private GovernanceAnnotationRules() {
    }

    /**
     * Every public method on a @Service-annotated class - excluding plain
     * accessors and Object overrides - must carry @Auditable.
     *
     * This is intentionally strict and meant as a starting point, not a
     * drop-in-and-forget rule: tune the excluded name patterns (or scope it
     * to a narrower package, e.g. only *.service.payment..) before enabling
     * in CI, or you'll get false-positive failures on genuinely non-governed
     * helper methods.
     */
    public static ArchRule publicServiceMethodsShouldBeAuditable(String basePackage) {
        return methods()
                .that().arePublic()
                .and().areDeclaredInClassesThat().areAnnotatedWith("org.springframework.stereotype.Service")
                .and().areDeclaredInClassesThat().resideInAPackage(basePackage + "..")
                .and().haveNameNotMatching("^(get|set|is)[A-Z].*")
                .and().haveNameNotMatching("(toString|equals|hashCode)")
                .should().beAnnotatedWith(AUDITABLE_ANNOTATION)
                .because("business-facing @Service methods must be governed by @Auditable " +
                        "(see governance-audit) - this rule is what makes it impossible " +
                        "to silently drop the annotation in a refactor without CI catching it");
    }
}
