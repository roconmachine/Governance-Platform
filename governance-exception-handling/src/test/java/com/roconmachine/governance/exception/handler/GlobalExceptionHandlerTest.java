package com.roconmachine.governance.exception.handler;

import com.roconmachine.governance.core.config.GovernanceCoreProperties;
import com.roconmachine.governance.exception.async.AsyncExceptionLogger;
import com.roconmachine.governance.exception.config.GovernanceExceptionHandlingProperties;
import com.roconmachine.governance.exception.exception.NotFoundException;
import com.roconmachine.governance.exception.model.ErrorResponse;
import com.roconmachine.governance.exception.model.ExceptionEvent;
import com.roconmachine.governance.exception.publisher.ExceptionEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private List<ExceptionEvent> capturedEvents;
    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        capturedEvents = new ArrayList<>();
        ExceptionEventPublisher capturingPublisher = capturedEvents::add;
        // Runnable::run executes synchronously in the test - we're testing the
        // handler's response-building logic here, not the async executor
        // itself (that's AsyncExceptionLoggerTest's job).
        AsyncExceptionLogger asyncExceptionLogger = new AsyncExceptionLogger(Runnable::run, capturingPublisher, true);

        GovernanceExceptionHandlingProperties properties = new GovernanceExceptionHandlingProperties();
        handler = new GlobalExceptionHandler(properties, new GovernanceCoreProperties(), asyncExceptionLogger);
    }

    @Test
    void businessExceptionMapsToItsOwnStatusAndErrorCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/payments/123");
        NotFoundException ex = new NotFoundException("PAYMENT_NOT_FOUND", "No payment with id 123");

        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getErrorCode()).isEqualTo("PAYMENT_NOT_FOUND");
        assertThat(response.getBody().getMessage()).isEqualTo("No payment with id 123");
        assertThat(response.getBody().getPath()).isEqualTo("/payments/123");
    }

    @Test
    void businessExceptionIsLoggedAsynchronously() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/payments/123");
        handler.handleBusinessException(new NotFoundException("PAYMENT_NOT_FOUND", "No payment with id 123"), request);

        assertThat(capturedEvents).hasSize(1);
        assertThat(capturedEvents.get(0).getExceptionClass()).isEqualTo(NotFoundException.class.getName());
        assertThat(capturedEvents.get(0).getStatus()).isEqualTo(404);
        // the async event carries the full exception detail even though the
        // client-facing ErrorResponse only carries the safe message
        assertThat(capturedEvents.get(0).getStackTrace()).contains("NotFoundException");
    }

    @Test
    void unexpectedExceptionReturnsGenericMessageRegardlessOfActualExceptionContent() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/whatever");
        RuntimeException sensitiveException = new RuntimeException("password=hunter2 in table users_secret");

        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(sensitiveException, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().getMessage()).doesNotContain("hunter2");
        assertThat(response.getBody().getErrorCode()).isEqualTo("INTERNAL_ERROR");
        // but the FULL detail still reaches the async log
        assertThat(capturedEvents.get(0).getMessage()).contains("hunter2");
    }

    @Test
    void debugDetailsAreOmittedFromResponseByDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(new RuntimeException("boom"), request);

        assertThat(response.getBody().getDebugDetails()).isNull();
    }

    @Test
    void debugDetailsAreIncludedWhenExplicitlyEnabled() {
        GovernanceExceptionHandlingProperties properties = new GovernanceExceptionHandlingProperties();
        properties.setIncludeStackTraceInResponse(true);
        GlobalExceptionHandler devHandler = new GlobalExceptionHandler(
                properties, new GovernanceCoreProperties(),
                new AsyncExceptionLogger(Runnable::run, event -> { }, true));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        ResponseEntity<ErrorResponse> response = devHandler.handleUnexpected(new RuntimeException("boom"), request);

        assertThat(response.getBody().getDebugDetails()).contains("RuntimeException");
    }
}
