package com.roconmachine.governance.response.trace;

import com.roconmachine.governance.core.config.GovernanceCoreProperties;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MicrometerTraceIdProviderTest {

    private final GovernanceCoreProperties coreProperties = new GovernanceCoreProperties();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void prefersTheActiveSpansTraceId() {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("span-trace-id-999");

        MicrometerTraceIdProvider provider = new MicrometerTraceIdProvider(tracer, coreProperties);

        assertThat(provider.currentTraceId()).isEqualTo("span-trace-id-999");
    }

    @Test
    void fallsBackToMdcWhenNoSpanIsActive() {
        Tracer tracer = mock(Tracer.class);
        when(tracer.currentSpan()).thenReturn(null);
        MDC.put(coreProperties.getMdcKey(), "fallback-correlation-id");

        MicrometerTraceIdProvider provider = new MicrometerTraceIdProvider(tracer, coreProperties);

        assertThat(provider.currentTraceId()).isEqualTo("fallback-correlation-id");
    }
}
