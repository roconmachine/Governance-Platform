package com.acme.payment.archunit;

import com.acme.payment.PaymentServiceDemoApplication;
import com.roconmachine.governance.archunit.rules.GovernanceAnnotationRules;
import com.roconmachine.governance.archunit.rules.GovernanceCodeHygieneRules;
import com.roconmachine.governance.archunit.rules.GovernanceLayeringRules;
import com.roconmachine.governance.archunit.rules.GovernanceNamingConventionRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * This is the per-service wiring governance-archunit's own README shows -
 * @AnalyzeClasses has to live on THIS class, in THIS service; it can't be
 * inherited from a shared base class (that's a real ArchUnit/JUnit5
 * limitation, not an oversight).
 */
@AnalyzeClasses(packagesOf = PaymentServiceDemoApplication.class,
                 importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureGovernanceTest {

    private static final String BASE_PACKAGE = "com.acme.payment";

    @ArchTest
    static final ArchRule controllersDoNotBypassTheServiceLayer =
            GovernanceLayeringRules.controllersMustNotDependOnRepositories(BASE_PACKAGE);

    @ArchTest
    static final ArchRule noCyclicPackageDependencies =
            GovernanceLayeringRules.noCyclicPackageDependencies(BASE_PACKAGE);

    @ArchTest
    static final ArchRule servicesNamedConsistently =
            GovernanceNamingConventionRules.classesInServicePackageShouldBeSuffixedService(BASE_PACKAGE);

    @ArchTest
    static final ArchRule controllersNamedConsistently =
            GovernanceNamingConventionRules.classesInControllerPackageShouldBeSuffixedController(BASE_PACKAGE);

    @ArchTest
    static final ArchRule constructorInjectionOnly =
            GovernanceCodeHygieneRules.constructorInjectionOnly(BASE_PACKAGE);

    @ArchTest
    static final ArchRule noStandardStreams =
            GovernanceCodeHygieneRules.noStandardStreamUsage(BASE_PACKAGE);

    // Scoped to the service package specifically (not the whole base package) -
    // see GovernanceAnnotationRules' Javadoc: this rule is strict and would
    // false-positive on plain accessor-style methods if applied too broadly.
    @ArchTest
    static final ArchRule publicServiceMethodsAreAuditable =
            GovernanceAnnotationRules.publicServiceMethodsShouldBeAuditable(BASE_PACKAGE + ".service");
}
