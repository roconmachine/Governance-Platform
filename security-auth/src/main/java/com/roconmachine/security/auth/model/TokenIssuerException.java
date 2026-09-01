package com.platform.security.issuer.model;

/** Base type for every failure this module raises. Unchecked, same philosophy as the rest of this platform. */
public class TokenIssuerException extends RuntimeException {
    public TokenIssuerException(String message) {
        super(message);
    }

    public TokenIssuerException(String message, Throwable cause) {
        super(message, cause);
    }
}
