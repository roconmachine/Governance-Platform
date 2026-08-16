package com.roconmachine.security.auth.jwt;

/**
 * Raised for any token validation failure - expired, malformed, bad
 * signature, wrong issuer/audience. The message is fine to log (server-side
 * only) but JwtAuthenticationFilter never echoes it back in an HTTP
 * response body: telling a caller exactly *why* their token failed
 * (expired vs. bad signature vs. wrong audience) hands an attacker useful
 * feedback for free.
 */
public class TokenValidationException extends RuntimeException {
    public TokenValidationException(String message) {
        super(message);
    }

    public TokenValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
