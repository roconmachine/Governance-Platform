package com.platform.governance.response.exception;

import org.springframework.http.HttpStatus;

/**
 * System/infrastructure failures - a downstream dependency, database, or
 * unexpected internal fault, as opposed to a business rule rejecting a
 * well-formed request. Type is always {@link ExceptionType#SYSTEM}.
 * Defaults to HTTP 500, but accepts an override for the rarer case of a
 * system failure with a more specific status (e.g. 503 for a downstream
 * dependency being unavailable).
 */
public class SystemException extends BaseAppException {

    public SystemException(String eventCode, String message) {
        this(eventCode, message, HttpStatus.INTERNAL_SERVER_ERROR, null, null);
    }

    public SystemException(String eventCode, String message, Throwable cause) {
        this(eventCode, message, HttpStatus.INTERNAL_SERVER_ERROR, null, cause);
    }

    public SystemException(String eventCode, String message, HttpStatus httpStatus, Object details, Throwable cause) {
        super(eventCode, ExceptionType.SYSTEM, message, httpStatus, details, cause);
    }

    public SystemException(ErrorCode errorCode) {
        this(errorCode.eventCode(), errorCode.defaultMessage(), errorCode.httpStatus(), null, null);
    }

    public SystemException(ErrorCode errorCode, Throwable cause) {
        this(errorCode.eventCode(), errorCode.defaultMessage(), errorCode.httpStatus(), null, cause);
    }
}
