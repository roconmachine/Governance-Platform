package com.platform.governance.archunit.rules;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Naming conventions - low drama, high value: consistent naming is what
 * makes the layering rules in {@link GovernanceLayeringRules} meaningful in
 * the first place (they rely on package structure lining up with role).
 */
public final class GovernanceNamingConventionRules {

    private GovernanceNamingConventionRules() {
    }

    public static ArchRule classesInServicePackageShouldBeSuffixedService(String basePackage) {
        return classes()
                .that().resideInAPackage(basePackage + ".service..")
                .and().areNotInterfaces()
                .and().areTopLevelClasses()
                .should().haveSimpleNameEndingWith("Service")
                .orShould().haveSimpleNameEndingWith("ServiceImpl")
                .because("consistent naming keeps the layering rules meaningful and makes " +
                        "code navigable across every service in the fleet");
    }

    public static ArchRule classesInControllerPackageShouldBeSuffixedController(String basePackage) {
        return classes()
                .that().resideInAPackage(basePackage + ".controller..")
                .and().areTopLevelClasses()
                .should().haveSimpleNameEndingWith("Controller")
                .because("consistent naming keeps the layering rules meaningful");
    }

    public static ArchRule classesInRepositoryPackageShouldBeSuffixedRepository(String basePackage) {
        return classes()
                .that().resideInAPackage(basePackage + ".repository..")
                .and().areTopLevelClasses()
                .should().haveSimpleNameEndingWith("Repository")
                .because("consistent naming keeps the layering rules meaningful");
    }
}
