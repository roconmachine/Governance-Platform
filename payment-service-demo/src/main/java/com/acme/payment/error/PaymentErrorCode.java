package com.acme.payment.error;

import com.roconmachine.governance.response.exception.ErrorCode;
import com.roconmachine.governance.response.exception.ExceptionType;
import org.springframework.http.HttpStatus;

/**
 * This service's own error code registry - the numbering convention
 * recommended platform-wide: 0001-0999 for business codes, 1001+ for
 * system/infrastructure codes, all in one enum so every code this service
 * can produce is greppable in one file. See governance-app-response's
 * README for the full explanation.
 */
public enum PaymentErrorCode implements ErrorCode {

    ACCOUNT_NOT_FOUND(          "0001", ExceptionType.BUSINESS, "Account not found",                       HttpStatus.NOT_FOUND),
    ACCOUNT_FROZEN(             "0002", ExceptionType.BUSINESS, "Account is frozen",                        HttpStatus.CONFLICT),
    INSUFFICIENT_FUNDS(        "0003", ExceptionType.BUSINESS, "Insufficient funds for this transfer",      HttpStatus.UNPROCESSABLE_ENTITY),
    DOWNSTREAM_EXCHANGE_UNAVAILABLE("1001", ExceptionType.SYSTEM, "Downstream Exchange/Graph service unavailable", HttpStatus.SERVICE_UNAVAILABLE);

    private final String eventCode;
    private final ExceptionType type;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    PaymentErrorCode(String eventCode, ExceptionType type, String defaultMessage, HttpStatus httpStatus) {
        this.eventCode = eventCode;
        this.type = type;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    @Override public String eventCode() { return eventCode; }
    @Override public ExceptionType type() { return type; }
    @Override public String defaultMessage() { return defaultMessage; }
    @Override public HttpStatus httpStatus() { return httpStatus; }
}
