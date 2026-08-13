package com.roconmachine.governance.response.handler;

import com.roconmachine.governance.response.config.ApplicationResponseProperties;
import com.roconmachine.governance.response.exception.BusinessException;
import com.roconmachine.governance.response.exception.ExceptionType;
import com.roconmachine.governance.response.exception.SystemException;
import com.roconmachine.governance.response.model.AppCodeFormatter;
import com.roconmachine.governance.response.model.AppResponse;
import com.roconmachine.governance.response.trace.TraceIdProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The one place every exception is formatted into {@link AppResponse}. See
 * this module's README for why it must not coexist with
 * governance-exception-handling's GlobalExceptionHandler in the same
 * service - both are @RestControllerAdvice beans that would claim
 * {@code Exception.class}.
 *
 * {@link BusinessException} keeps its own declared HTTP status (typically
 * 4xx); {@link SystemException} and any other unhandled exception are
 * always forced to {@code Type=S} and, for the fully-generic fallback,
 * HTTP 500 - regardless of what a careless throw site might have
 * attempted, an unexpected fault is never reported as a business (4xx)
 * error to a caller.
 */
@RestControllerAdvice
public class GlobalAppExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalAppExceptionHandler.class);

    private final ApplicationResponseProperties properties;
    private final TraceIdProvider traceIdProvider;

    public GlobalAppExceptionHandler(ApplicationResponseProperties properties, TraceIdProvider traceIdProvider) {
        this.properties = properties;
        this.traceIdProvider = traceIdProvider;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<AppResponse<Object>> handleBusiness(BusinessException ex) {
        log.warn("Business exception: appCode={} message={}",
                formatAppCode(ExceptionType.BUSINESS.getCode(), ex.getEventCode()), ex.getMessage());
        return buildErrorResponse(ex.getHttpStatus(), ExceptionType.BUSINESS.getCode(), ex.getEventCode(),
                ex.getMessage(), ex.getDetails());
    }

    @ExceptionHandler(SystemException.class)
    public ResponseEntity<AppResponse<Object>> handleSystem(SystemException ex) {
        // Full detail, including cause, server-side - the response body stays
        // limited to what SystemException's own message/details declare.
        log.error("System exception: appCode={} message={}",
                formatAppCode(ExceptionType.SYSTEM.getCode(), ex.getEventCode()), ex.getMessage(), ex);
        return buildErrorResponse(ex.getHttpStatus(), ExceptionType.SYSTEM.getCode(), ex.getEventCode(),
                ex.getMessage(), ex.getDetails());
    }

    /**
     * The last-resort catch-all for anything that isn't a BaseAppException
     * at all - forced to Type=S, HTTP 500, and a generic message regardless
     * of what the underlying exception says, since an unexpected
     * NullPointerException/SQLException message can easily contain
     * internal details that shouldn't reach a caller. Full exception detail
     * still goes to the server-side log.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<AppResponse<Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception - formatted as system error {}",
                formatAppCode(ExceptionType.SYSTEM.getCode(), properties.getDefaultSystemEventCode()), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ExceptionType.SYSTEM.getCode(),
                properties.getDefaultSystemEventCode(), "An unexpected error occurred", null);
    }

    private ResponseEntity<AppResponse<Object>> buildErrorResponse(HttpStatus status, String typeCode,
                                                                     String eventCode, String message, Object details) {
        String appCode = formatAppCode(typeCode, eventCode);
        AppResponse<Object> body = AppResponse.error(status.value(), appCode, message, details,
                traceIdProvider.currentTraceId());
        return ResponseEntity.status(status).body(body);
    }

    private String formatAppCode(String typeCode, String eventCode) {
        return AppCodeFormatter.format(properties.getServiceId(), typeCode, eventCode);
    }
}
