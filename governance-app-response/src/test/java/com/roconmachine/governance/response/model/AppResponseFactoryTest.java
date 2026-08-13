package com.roconmachine.governance.response.model;

import com.roconmachine.governance.response.config.ApplicationResponseProperties;
import com.roconmachine.governance.response.trace.TraceIdProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppResponseFactoryTest {

    @Test
    void formatMatchesTheServiceIdTypeEventCodeSpec() {
        assertThat(AppCodeFormatter.format("PAY", "B", "0001")).isEqualTo("PAY-B-0001");
        assertThat(AppCodeFormatter.format("PAY", AppCodeFormatter.INFO_TYPE, "0000")).isEqualTo("PAY-I-0000");
    }

    @Test
    void successFactoryBuildsTheInfoAppCodeFromConfiguredProperties() {
        ApplicationResponseProperties properties = new ApplicationResponseProperties();
        properties.setServiceId("ORD");
        properties.setSuccessEventCode("0000");
        TraceIdProvider traceIdProvider = () -> "trace-xyz";

        AppResponseFactory factory = new AppResponseFactory(properties, traceIdProvider);
        AppResponse<String> response = factory.success("payload", "Order created");

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getAppCode()).isEqualTo("ORD-I-0000");
        assertThat(response.getHttpCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo("payload");
        assertThat(response.getMessage()).isEqualTo("Order created");
        assertThat(response.getTraceId()).isEqualTo("trace-xyz");
        assertThat(response.getTimestamp()).isNotNull();
    }
}
