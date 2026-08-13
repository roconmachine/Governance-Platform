package com.platform.governance.response.exception;

import org.springframework.http.HttpStatus;

import java.util.regex.Pattern;

/**
 * Base type for every exception this framework formats into the
 * {@code ServiceID-Type-EventCode} appCode. Deliberately validates
 * {@code eventCode}'s shape (exactly 4 digits) in the constructor, not
 * later when {@code GlobalAppExceptionHandler} formats the response - a
 * malformed event code is a bug in the throwing code, and should fail loud
 * and immediately at the throw site during development/testing, not
 * silently produce a malformed appCode in a response body in production.
 */
public abstract class BaseAppException extends RuntimeException {

    private static final Pattern EVENT_CODE_PATTERN = Pattern.compile("\\d{4}");

    private final String eventCode;
    private final ExceptionType type;
    private final HttpStatus httpStatus;
    private final Object details;

    protected BaseAppException(String eventCode, ExceptionType type, String message,
                                HttpStatus httpStatus, Object details, Throwable cause) {
        super(message, cause);
        this.eventCode = requireValidEventCode(eventCode);
        this.type = type;
        this.httpStatus = httpStatus;
        this.details = details;
    }

    private static String requireValidEventCode(String eventCode) {
        if (eventCode == null || !EVENT_CODE_PATTERN.matcher(eventCode).matches()) {
            throw new IllegalArgumentException(
                    "eventCode must be exactly 4 digits (e.g. \"0001\"), but was: " + eventCode);
        }
        return eventCode;
    }

    public String getEventCode() { return eventCode; }
    public ExceptionType getType() { return type; }
    public HttpStatus getHttpStatus() { return httpStatus; }
    public Object getDetails() { return details; }
}
