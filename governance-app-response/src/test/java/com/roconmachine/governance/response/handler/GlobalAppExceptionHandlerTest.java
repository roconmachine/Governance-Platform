package com.roconmachine.governance.response.handler;

import com.roconmachine.governance.response.config.ApplicationResponseProperties;
import com.roconmachine.governance.response.exception.BusinessException;
import com.roconmachine.governance.response.exception.SystemException;
import com.roconmachine.governance.response.model.AppResponse;
import com.roconmachine.governance.response.trace.TraceIdProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalAppExceptionHandlerTest {

    private GlobalAppExceptionHandler handler;

    @BeforeEach
    void setUp() {
        ApplicationResponseProperties properties = new ApplicationResponseProperties();
        properties.setServiceId("PAY");
        properties.setDefaultSystemEventCode("9999");
        TraceIdProvider traceIdProvider = () -> "trace-abc-123";
        handler = new GlobalAppExceptionHandler(properties, traceIdProvider);
    }

    @Test
    void businessExceptionFormatsAppCodeWithBType() {
        BusinessException ex = new BusinessException("0001", "Insufficient funds", HttpStatus.UNPROCESSABLE_ENTITY);
        ResponseEntity<AppResponse<Object>> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getAppCode()).isEqualTo("PAY-B-0001");
        assertThat(response.getBody().getStatus()).isEqualTo("ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("Insufficient funds");
        assertThat(response.getBody().getTraceId()).isEqualTo("trace-abc-123");
    }

    @Test
    void systemExceptionFormatsAppCodeWithSType() {
        SystemException ex = new SystemException("1001", "Downstream unavailable");
        ResponseEntity<AppResponse<Object>> response = handler.handleSystem(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getAppCode()).isEqualTo("PAY-S-1001");
    }

    @Test
    void unhandledExceptionIsForcedToSystemTypeWithGenericMessage() {
        RuntimeException sensitive = new RuntimeException("password=hunter2 in table users_secret");
        ResponseEntity<AppResponse<Object>> response = handler.handleUnexpected(sensitive);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getAppCode()).isEqualTo("PAY-S-9999"); // default system event code, never business
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().getMessage()).doesNotContain("hunter2");
    }

    @Test
    void errorResponseCarriesDetailsPayloadWhenPresent() {
        record ValidationDetail(String field, String reason) {}
        BusinessException ex = new BusinessException("0002", "Validation failed", HttpStatus.BAD_REQUEST,
                new ValidationDetail("amount", "must be positive"));

        ResponseEntity<AppResponse<Object>> response = handler.handleBusiness(ex);

        assertThat(response.getBody().getData()).isInstanceOf(ValidationDetail.class);
    }
}
