package com.roconmachine.securityauth.core;

/**
 * A freshly issued access/refresh token pair. Returned by {@link RefreshTokenService}
 * on initial login and on every successful rotation.
 */
public record TokenPair(String accessToken, String refreshToken) {
}
