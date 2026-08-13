package com.roconmachine.governance.audit.annotation;

import java.lang.annotation.*;

/**
 * Declares that a method is a governed business action: every invocation
 * produces an {@link com.roconmachine.governance.audit.model.AuditEvent}
 * capturing actor, action, resource, outcome, duration and correlation id.
 *
 * This is the "code instead of a document" contract: instead of a compliance
 * checklist saying "all fund transfers must be audited", the transfer method
 * simply carries this annotation and the platform guarantees the rest.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Auditable {

    /** Business action name, e.g. "FUNDS_TRANSFER", "KYC_UPDATE". Defaults to the method name if blank. */
    String action() default "";

    /** Logical resource/domain this action touches, e.g. "ACCOUNT", "PAYMENT". */
    String resource() default "";

    /** Whether method arguments are included (masked per @Sensitive) in the audit event. */
    boolean captureArgs() default true;

    /** Whether the return value is included (masked per @Sensitive) in the audit event. */
    boolean captureResult() default false;

    /**
     * If true (the default), the audit event is published on a bounded
     * background executor instead of the calling thread - the annotated
     * method returns without waiting on whatever the configured
     * AuditEventPublisher does (log I/O, a network call to a central audit
     * store, etc.). Set this to false for a specific method when the audit
     * write must be confirmed BEFORE the method returns - e.g. a
     * compliance-critical action where "the audit record might land a
     * moment after the business action completed" is not an acceptable
     * trade-off. See governance-audit's README for the full explanation of
     * what changes when this is false, including that
     * governance.audit.fail-on-publish-error only has any effect on the
     * caller when async=false.
     */
    boolean async() default true;
}
