package com.platform.governance.response.trace;

import com.platform.governance.core.config.GovernanceCoreProperties;
import org.slf4j.MDC;

/**
 * The zero-extra-infra default: reads governance-core's correlation-id MDC
 * key (populated by its CorrelationIdFilter on every request) rather than
 * inventing a separate trace-id concept. Registered only when Micrometer
 * Tracing's {@code Tracer} type is NOT on the classpath - see
 * {@code ApplicationResponseAutoConfiguration}'s {@code @ConditionalOnMissingClass}
 * gate, mutually exclusive with {@link MicrometerTraceIdProvider} by design.
 */
public class MdcTraceIdProvider implements TraceIdProvider {

    private final GovernanceCoreProperties coreProperties;

    public MdcTraceIdProvider(GovernanceCoreProperties coreProperties) {
        this.coreProperties = coreProperties;
    }

    @Override
    public String currentTraceId() {
        return MDC.get(coreProperties.getMdcKey());
    }
}
