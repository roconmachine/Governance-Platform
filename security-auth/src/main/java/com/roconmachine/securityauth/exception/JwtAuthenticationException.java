package com.roconmachine.securityauth.exception;

/**
 * Raised for JWT-related authentication failures that host applications may want to
 * catch and map to a specific HTTP response (e.g. via an {@code @ExceptionHandler} or
 * a custom {@code AuthenticationEntryPoint}).
 */
public class JwtAuthenticationException extends RuntimeException {

    public JwtAuthenticationException(String message) {
        super(message);
    }

    public JwtAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
