package com.platform.governance.archunit.rules;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GovernanceCodeHygieneRulesTest {

    private static final String GOOD_PACKAGE = "com.platform.governance.archunit.fixtures.good";
    private static final String BAD_PACKAGE = "com.platform.governance.archunit.fixtures.bad";

    private final JavaClasses goodClasses = new ClassFileImporter().importPackages(GOOD_PACKAGE);
    private final JavaClasses badClasses = new ClassFileImporter().importPackages(BAD_PACKAGE);

    @Test
    void constructorInjectionOnly_passesWhenNoFieldInjectionUsed() {
        assertThatCode(() ->
                GovernanceCodeHygieneRules.constructorInjectionOnly(GOOD_PACKAGE).check(goodClasses)
        ).doesNotThrowAnyException();
    }

    @Test
    void constructorInjectionOnly_failsOnAutowiredField() {
        assertThatThrownBy(() ->
                GovernanceCodeHygieneRules.constructorInjectionOnly(BAD_PACKAGE).check(badClasses)
        ).isInstanceOf(AssertionError.class);
    }

    @Test
    void noStandardStreamUsage_passesWhenNoSystemOutUsed() {
        assertThatCode(() ->
                GovernanceCodeHygieneRules.noStandardStreamUsage(GOOD_PACKAGE).check(goodClasses)
        ).doesNotThrowAnyException();
    }

    @Test
    void noStandardStreamUsage_failsOnSystemOutUsage() {
        assertThatThrownBy(() ->
                GovernanceCodeHygieneRules.noStandardStreamUsage(BAD_PACKAGE).check(badClasses)
        ).isInstanceOf(AssertionError.class);
    }
}
