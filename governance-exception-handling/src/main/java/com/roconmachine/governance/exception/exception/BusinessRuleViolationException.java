package com.roconmachine.governance.exception.exception;

import org.springframework.http.HttpStatus;

/** A request is well-formed but violates a business rule, e.g. insufficient funds. */
public class BusinessRuleViolationException extends BusinessException {
    public BusinessRuleViolationException(String errorCode, String message) {
        super(errorCode, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
