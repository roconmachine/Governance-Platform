package com.roconmachine.governance.response.exception;

import org.springframework.http.HttpStatus;

/**
 * Contract for a service's own domain-specific error codes - implement this
 * as an enum per service (see the module README for a full
 * {@code PaymentErrorCode} example) so every {@code throw} site references
 * a named constant instead of a magic string, while still flowing through
 * {@link BusinessException}/{@link SystemException} into the standard
 * {@code ServiceID-Type-EventCode} format automatically.
 *
 * <pre>{@code
 * public enum PaymentErrorCode implements ErrorCode {
 *     INSUFFICIENT_FUNDS("0001", ExceptionType.BUSINESS, "Insufficient funds for this transfer", HttpStatus.UNPROCESSABLE_ENTITY),
 *     ACCOUNT_NOT_FOUND ("0002", ExceptionType.BUSINESS, "Account not found",                     HttpStatus.NOT_FOUND);
 *     // ... implement the four accessor methods from a constructor, as usual for a Java enum
 * }
 *
 * throw new BusinessException(PaymentErrorCode.INSUFFICIENT_FUNDS);
 * }</pre>
 */
public interface ErrorCode {

    /** Exactly 4 digits, e.g. "0001". Validated by {@link BaseAppException} regardless of what a careless implementation returns. */
    String eventCode();

    ExceptionType type();

    String defaultMessage();

    HttpStatus httpStatus();
}
