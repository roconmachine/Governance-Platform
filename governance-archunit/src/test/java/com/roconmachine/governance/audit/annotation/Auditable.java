package com.roconmachine.governance.audit.annotation;

import java.lang.annotation.*;

/**
 * TEST FIXTURE ONLY. Mirrors the fully-qualified name of the real
 * {@code Auditable} annotation in governance-audit, so
 * {@link com.roconmachine.governance.archunit.rules.GovernanceAnnotationRules}
 * (which matches by annotation name, not by class reference - see that
 * class's Javadoc) can be exercised in this module's own tests without this
 * module taking a compile dependency on governance-audit.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Auditable {
}
