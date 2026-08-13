package com.platform.governance.response.exception;

import org.springframework.http.HttpStatus;

/**
 * Domain/business-rule violations - the request was well-formed but the
 * domain rejects it (insufficient funds, duplicate resource, account
 * frozen). Type is always {@link ExceptionType#BUSINESS} - there is no
 * constructor path that lets a caller override it, so a
 * {@code BusinessException} can never accidentally format as a system (S)
 * code.
 */
public class BusinessException extends BaseAppException {

    public BusinessException(String eventCode, String message, HttpStatus httpStatus) {
        this(eventCode, message, httpStatus, null, null);
    }

    public BusinessException(String eventCode, String message, HttpStatus httpStatus, Object details) {
        this(eventCode, message, httpStatus, details, null);
    }

    public BusinessException(String eventCode, String message, HttpStatus httpStatus, Object details, Throwable cause) {
        super(eventCode, ExceptionType.BUSINESS, message, httpStatus, details, cause);
    }

    /** Uses the ErrorCode's own default message and status - the common case. */
    public BusinessException(ErrorCode errorCode) {
        this(errorCode.eventCode(), errorCode.defaultMessage(), errorCode.httpStatus(), null, null);
    }

    /** Overrides the default message (e.g. to include a specific account id) while keeping the code/status from ErrorCode. */
    public BusinessException(ErrorCode errorCode, String messageOverride) {
        this(errorCode.eventCode(), messageOverride, errorCode.httpStatus(), null, null);
    }

    /** Uses the default message but attaches a details payload (e.g. field-level validation errors). */
    public BusinessException(ErrorCode errorCode, Object details) {
        this(errorCode.eventCode(), errorCode.defaultMessage(), errorCode.httpStatus(), details, null);
    }
}
