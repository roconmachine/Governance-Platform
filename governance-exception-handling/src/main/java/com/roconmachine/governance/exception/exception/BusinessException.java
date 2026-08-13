package com.roconmachine.governance.exception.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for domain/business errors a service wants mapped through the
 * standard {@link com.roconmachine.governance.exception.model.ErrorResponse}
 * envelope. Extend this rather than throwing a raw RuntimeException or
 * hand-building a ResponseEntity in a controller - that's what keeps every
 * service's error shape identical.
 *
 * <pre>{@code
 * throw new NotFoundException("PAYMENT_NOT_FOUND", "No payment with id " + id);
 * }</pre>
 */
public abstract class BusinessException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    protected BusinessException(String errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    protected BusinessException(String errorCode, HttpStatus httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() { return errorCode; }
    public HttpStatus getHttpStatus() { return httpStatus; }
}
