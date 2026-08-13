package com.roconmachine.governance.archunit.rules;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GovernanceAnnotationRulesTest {

    private static final String GOOD_PACKAGE = "com.roconmachine.governance.archunit.fixtures.good";
    private static final String BAD_PACKAGE = "com.roconmachine.governance.archunit.fixtures.bad";

    private final JavaClasses goodClasses = new ClassFileImporter().importPackages(GOOD_PACKAGE);
    private final JavaClasses badClasses = new ClassFileImporter().importPackages(BAD_PACKAGE);

    @Test
    void publicServiceMethodsShouldBeAuditable_passesWhenAnnotationPresent() {
        assertThatCode(() ->
                GovernanceAnnotationRules.publicServiceMethodsShouldBeAuditable(GOOD_PACKAGE).check(goodClasses)
        ).doesNotThrowAnyException();
    }

    @Test
    void publicServiceMethodsShouldBeAuditable_failsWhenAnnotationMissing() {
        assertThatThrownBy(() ->
                GovernanceAnnotationRules.publicServiceMethodsShouldBeAuditable(BAD_PACKAGE).check(badClasses)
        ).isInstanceOf(AssertionError.class);
    }
}
