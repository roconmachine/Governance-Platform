package com.platform.security.exchange.service;

import com.platform.security.exchange.exception.TokenAcquisitionException;

/**
 * What a consuming service actually calls. Autowire this interface - never
 * the implementation class directly - so the acquisition/caching/validation
 * mechanics stay swappable behind {@code @ConditionalOnMissingBean}.
 */
public interface ExchangeAuthProvider {

    /**
     * Client Credentials flow: app-only access, no user present. Returns a
     * cached token when one is available and not close to expiry (see
     * {@code exchange.auth.cache.expiry-buffer-seconds}); otherwise acquires
     * a fresh one from Entra ID and caches it.
     *
     * @throws TokenAcquisitionException if Entra ID rejects the request or is unreachable
     */
    String getAccessToken();

    /**
     * On-Behalf-Of flow: exchanges a user's token (already validated by
     * whatever authenticated the inbound request - e.g. security-auth) for
     * a new token scoped to call a downstream resource (Exchange
     * Online/Graph) as that same user.
     *
     * @param userAssertionToken the inbound user token to exchange - never logged or included in exception messages
     * @throws TokenAcquisitionException if Entra ID rejects the exchange (e.g. consent not granted, invalid assertion) or is unreachable
     * @throws IllegalArgumentException if userAssertionToken is null or blank
     */
    String getAccessTokenOnBehalfOf(String userAssertionToken);

    /**
     * Validates an arbitrary Entra-issued token's signature (against the
     * tenant's JWKS), expiry, and - if {@code exchange.auth.validation.expected-audience}
     * is configured - audience. Never throws; returns false for any
     * validation failure, malformed input, or network error reaching the
     * JWKS endpoint.
     */
    boolean validateToken(String token);
}
