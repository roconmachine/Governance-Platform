package com.platform.governance.archunit.rules;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class GovernanceNamingConventionRulesTest {

    private static final String GOOD_PACKAGE = "com.platform.governance.archunit.fixtures.good";

    private final JavaClasses goodClasses = new ClassFileImporter().importPackages(GOOD_PACKAGE);

    @Test
    void servicesFollowNamingConvention() {
        assertThatCode(() ->
                GovernanceNamingConventionRules.classesInServicePackageShouldBeSuffixedService(GOOD_PACKAGE).check(goodClasses)
        ).doesNotThrowAnyException();
    }

    @Test
    void controllersFollowNamingConvention() {
        assertThatCode(() ->
                GovernanceNamingConventionRules.classesInControllerPackageShouldBeSuffixedController(GOOD_PACKAGE).check(goodClasses)
        ).doesNotThrowAnyException();
    }

    @Test
    void repositoriesFollowNamingConvention() {
        assertThatCode(() ->
                GovernanceNamingConventionRules.classesInRepositoryPackageShouldBeSuffixedRepository(GOOD_PACKAGE).check(goodClasses)
        ).doesNotThrowAnyException();
    }
}
