package com.platform.governance.exception.model;

import java.time.Instant;
import java.util.List;

/**
 * The one error shape every service using this module returns, for every
 * kind of failure - a caller integrating against ANY service on this
 * platform parses exactly one envelope, not a different ad-hoc JSON shape
 * per team. Deliberately never carries a raw stack trace or internal
 * exception class name by default - see {@code debugDetails} for the one
 * opt-in, non-production exception to that rule.
 */
public final class ErrorResponse {

    private final Instant timestamp;
    private final String correlationId;
    private final int status;
    private final String errorCode;
    private final String message;
    private final String path;
    private final List<FieldValidationError> validationErrors;

    /**
     * Only populated when governance.exception-handling.include-stack-trace-in-response
     * is explicitly turned on - never on by default, and should only ever be
     * turned on in a local/dev profile, never anywhere handling real traffic.
     */
    private final String debugDetails;

    public ErrorResponse(Instant timestamp, String correlationId, int status, String errorCode,
                          String message, String path, List<FieldValidationError> validationErrors,
                          String debugDetails) {
        this.timestamp = timestamp;
        this.correlationId = correlationId;
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
        this.path = path;
        this.validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
        this.debugDetails = debugDetails;
    }

    public Instant getTimestamp() { return timestamp; }
    public String getCorrelationId() { return correlationId; }
    public int getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
    public List<FieldValidationError> getValidationErrors() { return validationErrors; }
    public String getDebugDetails() { return debugDetails; }
}
