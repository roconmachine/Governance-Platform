package com.platform.security.exchange.exception;

/**
 * Base type for every failure this module raises. Unchecked deliberately -
 * a failure to reach Entra ID or acquire a token is an infrastructure
 * failure most call sites can't meaningfully recover from inline; let it
 * propagate to whatever top-level error handling the consuming service uses
 * (e.g. governance-exception-handling's GlobalExceptionHandler, if present).
 */
public class ExchangeAuthException extends RuntimeException {
    public ExchangeAuthException(String message) {
        super(message);
    }

    public ExchangeAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
