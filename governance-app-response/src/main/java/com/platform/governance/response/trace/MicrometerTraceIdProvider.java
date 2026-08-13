package com.platform.governance.response.trace;

import com.platform.governance.core.config.GovernanceCoreProperties;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;

/**
 * Prefers the active span's trace id from Micrometer Tracing when one
 * exists (correct across an entire distributed trace, not just this
 * service's own correlation id), falling back to governance-core's MDC key
 * if no span is active on the current thread.
 *
 * {@code micrometer-tracing} is an OPTIONAL dependency of this module - this
 * is the ONLY class allowed to reference {@link Tracer}/{@link Span}
 * directly, and it is only ever instantiated (loaded/verified by the JVM)
 * when {@code ApplicationResponseAutoConfiguration}'s
 * {@code @ConditionalOnClass(name = "io.micrometer.tracing.Tracer")} nested
 * configuration has already confirmed, via ASM bytecode-metadata inspection
 * rather than classloading, that the dependency is genuinely present. This
 * is the exact isolation lesson this platform learned the hard way with
 * {@code SecurityContextHolder} in an earlier module: a class merely
 * referencing an optional type can fail with {@code NoClassDefFoundError}
 * at class-verification time even if no code path ever executes that
 * reference - so the class must never be loaded at all when the dependency
 * is absent, not "loaded but careful."
 */
public class MicrometerTraceIdProvider implements TraceIdProvider {

    private final Tracer tracer;
    private final GovernanceCoreProperties coreProperties;

    public MicrometerTraceIdProvider(Tracer tracer, GovernanceCoreProperties coreProperties) {
        this.tracer = tracer;
        this.coreProperties = coreProperties;
    }

    @Override
    public String currentTraceId() {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            return currentSpan.context().traceId();
        }
        return MDC.get(coreProperties.getMdcKey());
    }
}
