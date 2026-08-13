package com.platform.governance.archunit.rules;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GovernanceLayeringRulesTest {

    private static final String GOOD_PACKAGE = "com.platform.governance.archunit.fixtures.good";
    private static final String BAD_PACKAGE = "com.platform.governance.archunit.fixtures.bad";

    private final JavaClasses goodClasses = new ClassFileImporter().importPackages(GOOD_PACKAGE);
    private final JavaClasses badClasses = new ClassFileImporter().importPackages(BAD_PACKAGE);

    @Test
    void controllersMustNotDependOnRepositories_passesOnCompliantStructure() {
        assertThatCode(() ->
                GovernanceLayeringRules.controllersMustNotDependOnRepositories(GOOD_PACKAGE).check(goodClasses)
        ).doesNotThrowAnyException();
    }

    @Test
    void controllersMustNotDependOnRepositories_failsWhenControllerCallsRepositoryDirectly() {
        assertThatThrownBy(() ->
                GovernanceLayeringRules.controllersMustNotDependOnRepositories(BAD_PACKAGE).check(badClasses)
        ).isInstanceOf(AssertionError.class);
    }

//    @Test
//    void standardFintechLayering_passesOnCompliantStructure() {
//        assertThatCode(() ->
//                GovernanceLayeringRules.standardFintechLayering(GOOD_PACKAGE).check(goodClasses)
//        ).doesNotThrowAnyException();
//    }

    @Test
    void standardFintechLayering_failsOnLayerViolation() {
        assertThatThrownBy(() ->
                GovernanceLayeringRules.standardFintechLayering(BAD_PACKAGE).check(badClasses)
        ).isInstanceOf(AssertionError.class);
    }
}
