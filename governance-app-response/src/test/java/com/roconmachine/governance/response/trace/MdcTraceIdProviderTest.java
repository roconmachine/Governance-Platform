package com.roconmachine.governance.response.trace;

import com.roconmachine.governance.core.config.GovernanceCoreProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTraceIdProviderTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void readsTraceIdFromGovernanceCoresCorrelationIdMdcKey() {
        GovernanceCoreProperties coreProperties = new GovernanceCoreProperties(); // mdcKey defaults to "correlationId"
        MDC.put(coreProperties.getMdcKey(), "abc-123");

        MdcTraceIdProvider provider = new MdcTraceIdProvider(coreProperties);

        assertThat(provider.currentTraceId()).isEqualTo("abc-123");
    }

    @Test
    void returnsNullWhenNoCorrelationIdIsPresent() {
        GovernanceCoreProperties coreProperties = new GovernanceCoreProperties();
        MdcTraceIdProvider provider = new MdcTraceIdProvider(coreProperties);

        assertThat(provider.currentTraceId()).isNull();
    }
}
