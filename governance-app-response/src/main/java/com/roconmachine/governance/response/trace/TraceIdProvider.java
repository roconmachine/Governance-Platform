package com.roconmachine.governance.response.trace;

/**
 * Resolves the trace id included in every {@link com.roconmachine.governance.response.model.AppResponse}.
 * Ships with two implementations, chosen automatically based on what's on
 * the classpath - see {@code ApplicationResponseAutoConfiguration} - and
 * can be replaced entirely with a {@code @ConditionalOnMissingBean} override.
 */
public interface TraceIdProvider {

    /** The current request's trace id, or null if none is available (no active span, no correlation id in MDC). */
    String currentTraceId();
}
