package com.roconmachine.governance.archunit.rules;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Layering rules parameterized by a service's own base package. These are
 * factories, not fixed rules bound to one codebase - each consuming service
 * calls e.g. {@code standardFintechLayering("com.acme.payment")} from its own
 * {@code @AnalyzeClasses}-annotated test class. See the module's README for
 * the exact wiring - {@code @AnalyzeClasses} must live on the consuming
 * service's test class itself; it isn't inherited from a shared base class.
 */
public final class GovernanceLayeringRules {

    private GovernanceLayeringRules() {
    }

    /**
     * Standard controller -> service -> repository/client layering assumed
     * across the platform's fintech middleware services. Adjust package
     * suffixes here (or write your own with {@link Architectures#layeredArchitecture()}
     * directly) if a service's structure genuinely differs.
     */
    public static ArchRule standardFintechLayering(String basePackage) {
        return Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Controller").definedBy(basePackage + ".controller..")
                .layer("Service").definedBy(basePackage + ".service..")
                .layer("Repository").definedBy(basePackage + ".repository..")
                .layer("Client").definedBy(basePackage + ".client..")
                .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
                .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
                .whereLayer("Client").mayOnlyBeAccessedByLayers("Service")
                .because("controllers must go through the service layer, and only the " +
                        "service layer may reach persistence/outbound clients - this is what " +
                        "guarantees governed concerns (e.g. @Auditable, @Sensitive masking) " +
                        "aren't bypassed by a shortcut call from a controller straight to a repository");
    }

    /**
     * Narrower, single-purpose version of the rule above for services that
     * don't want the full layered-architecture check yet but do want this one
     * guarantee enforced immediately.
     */
    public static ArchRule controllersMustNotDependOnRepositories(String basePackage) {
        return noClasses()
                .that().resideInAPackage(basePackage + ".controller..")
                .should().dependOnClassesThat().resideInAPackage(basePackage + ".repository..")
                .because("controllers must go through the service layer - direct repository " +
                        "access skips governed business rules such as @Auditable");
    }

    /** Detects package-level cycles, a common source of untestable, unreviewable coupling. */
    public static ArchRule noCyclicPackageDependencies(String basePackage) {
        return SlicesRuleDefinition.slices()
                .matching(basePackage + ".(*)..")
                .should().beFreeOfCycles();
    }
}
