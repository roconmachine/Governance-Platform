package com.roconmachine.governance.core.annotation;

import java.lang.annotation.*;

/**
 * Marks a field as sensitive (PII/PCI scope) so governance masking is applied
 * automatically wherever the containing object flows through any
 * governance module (audit logging, HTTP request/response logging, etc.) -
 * defined once here so every module masks the same field the same way.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface Sensitive {

    MaskStrategy strategy() default MaskStrategy.PARTIAL;

    enum MaskStrategy {
        /** Replace the whole value, e.g. "****". */
        FULL,
        /** Keep the last 4 characters visible, e.g. card PAN "**** **** **** 1234". */
        PARTIAL,
        /** Replace with a one-way hash - useful when you need to correlate without exposing the value. */
        HASH
    }
}
