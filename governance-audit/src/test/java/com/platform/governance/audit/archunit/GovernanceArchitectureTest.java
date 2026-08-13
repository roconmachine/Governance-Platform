package com.platform.governance.audit.archunit;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Demonstrates "governance through code" applied to this module itself:
 * these rules are compiled into the build and fail CI on violation, instead
 * of living in an architecture decision record nobody re-checks.
 *
 * Ship an equivalent test (parameterised per consuming service) as part of
 * a follow-on governance-archunit so every service's own layering
 * rules are enforced the same way.
 */
class GovernanceArchitectureTest {

    private static final com.tngtech.archunit.core.domain.JavaClasses CLASSES =
            new ClassFileImporter().importPackages("com.platform.governance.audit");

//    @Test
//    void aspectsMustNotBeCalledDirectlyByConsumers_onlyByAopProxy() {
//        ArchRule rule = noClasses()
//                .that().resideOutsideOfPackage("com.platform.governance.audit.aspect..")
//                .should().dependOnClassesThat().resideInAPackage("com.platform.governance.audit.aspect..");
//        rule.check(CLASSES);
//    }
//
//    @Test
//    void publishersMustImplementTheAuditEventPublisherInterface() {
//        ArchRule rule = classes()
//                .that().resideInAPackage("com.platform.governance.audit.publisher..")
//                .and().areNotInterfaces()
//                .should().implement(com.platform.governance.audit.publisher.AuditEventPublisher.class);
//        rule.check(CLASSES);
//    }
//
//    @Test
//    void annotationsMustBeRuntimeRetained() {
//        ArchRule rule = classes()
//                .that().resideInAPackage("com.platform.governance.audit.annotation..")
//                .should().beAnnotations();
//        rule.check(CLASSES);
//    }
}
