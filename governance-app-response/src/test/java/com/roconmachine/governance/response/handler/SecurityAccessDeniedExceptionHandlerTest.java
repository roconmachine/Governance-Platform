package com.roconmachine.governance.response.handler;

import com.roconmachine.governance.response.config.ApplicationResponseProperties;
import com.roconmachine.governance.response.model.AppResponse;
import com.roconmachine.governance.response.trace.TraceIdProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAccessDeniedExceptionHandlerTest {

    @Test
    void mapsAccessDeniedExceptionTo403WithTheStandardAppCodeShape() {
        ApplicationResponseProperties properties = new ApplicationResponseProperties();
        properties.setServiceId("PAY");
        TraceIdProvider traceIdProvider = () -> "trace-123";

        SecurityAccessDeniedExceptionHandler handler =
                new SecurityAccessDeniedExceptionHandler(properties, traceIdProvider);

        ResponseEntity<AppResponse<Object>> response =
                handler.handleAccessDenied(new AccessDeniedException("denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getAppCode()).isEqualTo("PAY-B-9403");
        assertThat(response.getBody().getStatus()).isEqualTo("ERROR");
    }
}
