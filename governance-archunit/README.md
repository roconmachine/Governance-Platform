# governance-archunit

Packages reusable [ArchUnit](https://www.archunit.org/) rule factories -
layering, naming, governance-annotation enforcement, and code hygiene - so
architectural policy is defined once and every service's own CI enforces it,
instead of each team writing (or quietly never writing) these rules from
scratch.

## This one works differently from the other modules

`governance-core`, `governance-audit`, and `governance-http-logging` are
runtime `@AutoConfiguration` modules - add the dependency, get the
behavior, nothing else to write.

This module is a **test-scope library**. ArchUnit's `@AnalyzeClasses`
annotation has to be declared directly on your own test class - it isn't
inherited from a shared base class - so there is a small amount of
copy-paste required per service. That's expected and by design; the value
this module adds is that the *rule logic* (what "good layering" or "must be
audited" means) lives in one place, not that you write zero lines of test
code.

## 1. Add the dependency (test scope)

```xml
<dependency>
    <groupId>com.platform.governance</groupId>
    <artifactId>governance-archunit</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

## 2. Wire it into your own architecture test

Create one test class per service - this is the part that's per-service:

```java
package com.acme.payment;

import com.platform.governance.archunit.rules.GovernanceAnnotationRules;
import com.platform.governance.archunit.rules.GovernanceCodeHygieneRules;
import com.platform.governance.archunit.rules.GovernanceLayeringRules;
import com.platform.governance.archunit.rules.GovernanceNamingConventionRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packagesOf = PaymentServiceApplication.class,
                 importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureGovernanceTest {

    private static final String BASE_PACKAGE = "com.acme.payment";

    @ArchTest
    static final ArchRule layering =
            GovernanceLayeringRules.standardFintechLayering(BASE_PACKAGE);

    @ArchTest
    static final ArchRule noCycles =
            GovernanceLayeringRules.noCyclicPackageDependencies(BASE_PACKAGE);

    @ArchTest
    static final ArchRule servicesNamedConsistently =
            GovernanceNamingConventionRules.classesInServicePackageShouldBeSuffixedService(BASE_PACKAGE);

    @ArchTest
    static final ArchRule constructorInjectionOnly =
            GovernanceCodeHygieneRules.constructorInjectionOnly(BASE_PACKAGE);

    @ArchTest
    static final ArchRule noStandardStreams =
            GovernanceCodeHygieneRules.noStandardStreamUsage(BASE_PACKAGE);

    // Tune before enabling - see the Javadoc on this rule; it's strict by
    // default and will false-positive on non-business helper methods until
    // you scope the base package or exclude patterns for your service.
    @ArchTest
    static final ArchRule serviceMethodsAreAuditable =
            GovernanceAnnotationRules.publicServiceMethodsShouldBeAuditable(BASE_PACKAGE + ".service.payment");
}
```

`mvn test` now fails your build the moment any of these are violated - a
teammate accidentally calling a repository straight from a controller, or
dropping `@Auditable` off a payment method in a refactor, is a red CI run,
not a silent regression waiting for an audit.

## What's included

| Class | Rules |
|---|---|
| `GovernanceLayeringRules` | `standardFintechLayering` (full controller→service→repository/client check), `controllersMustNotDependOnRepositories` (narrower, standalone), `noCyclicPackageDependencies` |
| `GovernanceNamingConventionRules` | `*ShouldBeSuffixedService/Controller/Repository` |
| `GovernanceAnnotationRules` | `publicServiceMethodsShouldBeAuditable` - the "governance on governance" rule tying back to `governance-audit`'s `@Auditable`, matched by annotation name so this module has zero compile dependency on that module |
| `GovernanceCodeHygieneRules` | `constructorInjectionOnly` (no `@Autowired` fields), `noStandardStreamUsage` (no `System.out`/`System.err` - bypasses the correlation-id/masking pipeline entirely) |

## Testing the rules themselves

This module's own `src/test` includes a `fixtures.good` package (fully
compliant) and a `fixtures.bad` package (deliberately violates every rule),
and asserts each rule factory passes on one and fails on the other - so
"the rule actually catches the thing it claims to catch" is itself verified,
not just assumed from the code reading correctly.

## Build

```
mvn clean test
```
