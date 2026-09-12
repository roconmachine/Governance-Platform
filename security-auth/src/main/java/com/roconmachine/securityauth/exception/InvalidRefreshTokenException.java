package com.roconmachine.securityauth.exception;

/**
 * Raised when a presented refresh token is unknown, expired, or otherwise not
 * currently valid for rotation. Distinct from {@link TokenReuseDetectedException},
 * which signals the more serious case of a token being replayed after rotation.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
