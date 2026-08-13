package com.roconmachine.governance.exception.handler;

import com.roconmachine.governance.core.config.GovernanceCoreProperties;
import com.roconmachine.governance.exception.async.AsyncExceptionLogger;
import com.roconmachine.governance.exception.config.GovernanceExceptionHandlingProperties;
import com.roconmachine.governance.exception.exception.BusinessException;
import com.roconmachine.governance.exception.model.ErrorResponse;
import com.roconmachine.governance.exception.model.ExceptionEvent;
import com.roconmachine.governance.exception.model.FieldValidationError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The one place every kind of unhandled/expected-but-uncaught exception is
 * mapped to {@link ErrorResponse}. A service using this module does not
 * write its own {@code @ExceptionHandler} methods for these cases - it just
 * throws {@link BusinessException} subclasses (or lets Bean Validation /
 * Spring MVC's own exceptions surface) and gets the same response shape
 * every other service on the platform returns.
 *
 * Every branch below does the same three things: build a safe-for-clients
 * {@link ErrorResponse}, hand a full-detail {@link ExceptionEvent} to
 * {@link AsyncExceptionLogger} (off the request thread), and return the
 * response. The AccessDeniedException case is deliberately NOT here - see
 * {@link SecurityAccessDeniedExceptionHandler}'s Javadoc for why that one
 * lives in its own isolated class instead.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final GovernanceExceptionHandlingProperties properties;
    private final GovernanceCoreProperties coreProperties;
    private final AsyncExceptionLogger asyncExceptionLogger;

    public GlobalExceptionHandler(GovernanceExceptionHandlingProperties properties,
                                   GovernanceCoreProperties coreProperties,
                                   AsyncExceptionLogger asyncExceptionLogger) {
        this.properties = properties;
        this.coreProperties = coreProperties;
        this.asyncExceptionLogger = asyncExceptionLogger;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        return respond(ex.getHttpStatus(), ex.getErrorCode(), ex.getMessage(), request, ex, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldValidationError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldValidationError(fe.getField(), fe.getDefaultMessage()))
                .collect(Collectors.toList());
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request failed validation", request, ex, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldValidationError> fieldErrors = ex.getConstraintViolations().stream()
                .map(this::toFieldError)
                .collect(Collectors.toList());
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request failed validation", request, ex, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedRequest(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request body could not be read", request, ex, List.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", ex.getMessage(), request, ex, List.of());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFound(NoHandlerFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "NO_HANDLER_FOUND",
                "No handler for " + ex.getHttpMethod() + " " + ex.getRequestURL(), request, ex, List.of());
    }

    /**
     * The last-resort catch-all. Deliberately returns a generic, static
     * message regardless of what the underlying exception actually says -
     * an unexpected NullPointerException or SQLException message can easily
     * contain internal details (table names, field names, library internals)
     * that shouldn't reach a caller. The FULL exception - message, type,
     * stack trace - still goes to the async log; only the client-facing
     * message is deliberately generic.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, properties.getDefaultErrorCode(),
                "An unexpected error occurred", request, ex, List.of());
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String errorCode, String message,
                                                    HttpServletRequest request, Exception ex,
                                                    List<FieldValidationError> validationErrors) {
        String correlationId = MDC.get(coreProperties.getMdcKey());
        String actor = MDC.get(coreProperties.getActorMdcKey());
        Instant now = Instant.now();

        String debugDetails = properties.isIncludeStackTraceInResponse() ? stackTraceOf(ex) : null;

        ErrorResponse response = new ErrorResponse(now, correlationId, status.value(), errorCode,
                message, request.getRequestURI(), validationErrors, debugDetails);

        asyncExceptionLogger.logAsync(new ExceptionEvent(now, correlationId, actor, ex.getClass().getName(),
                ex.getMessage(), request.getRequestURI(), request.getMethod(), status.value(), stackTraceOf(ex)));

        return ResponseEntity.status(status).body(response);
    }

    private FieldValidationError toFieldError(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
        return new FieldValidationError(path, violation.getMessage());
    }

    private String stackTraceOf(Throwable ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
