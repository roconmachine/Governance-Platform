package com.platform.security.exchange.service;

import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.IConfidentialClientApplication;
import com.microsoft.aad.msal4j.OnBehalfOfParameters;
import com.microsoft.aad.msal4j.UserAssertion;
import com.roconmachine.governance.core.config.GovernanceCoreProperties;
import com.platform.security.exchange.cache.TokenCacheManager;
import com.platform.security.exchange.config.ExchangeAuthProperties;
import com.platform.security.exchange.exception.TokenAcquisitionException;
import com.platform.security.exchange.validation.EntraTokenValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletionException;

/**
 * Thread-safety: this class holds no mutable state of its own beyond its
 * (all thread-safe) collaborators - {@link IConfidentialClientApplication}
 * is documented by MSAL4J as safe for concurrent use and is shared as a
 * singleton, {@link TokenCacheManager} is backed by Caffeine (thread-safe),
 * and {@link EntraTokenValidator} is stateless apart from its own
 * thread-safe collaborators. Safe to call from multiple request threads
 * concurrently without external synchronization.
 */
public class DefaultExchangeAuthProvider implements ExchangeAuthProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultExchangeAuthProvider.class);

    private final IConfidentialClientApplication clientApplication;
    private final ExchangeAuthProperties properties;
    private final GovernanceCoreProperties coreProperties;
    private final TokenCacheManager tokenCache;
    private final EntraTokenValidator tokenValidator;

    public DefaultExchangeAuthProvider(IConfidentialClientApplication clientApplication,
                                        ExchangeAuthProperties properties,
                                        GovernanceCoreProperties coreProperties,
                                        TokenCacheManager tokenCache,
                                        EntraTokenValidator tokenValidator) {
        this.clientApplication = clientApplication;
        this.properties = properties;
        this.coreProperties = coreProperties;
        this.tokenCache = tokenCache;
        this.tokenValidator = tokenValidator;
    }

    @Override
    public String getAccessToken() {
        Set<String> scopes = scopeSet();
        String cacheKey = "client-credentials:" + String.join(",", scopes);

        if (properties.isCacheEnabled()) {
            String cached = tokenCache.getIfPresent(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        try {
            ClientCredentialParameters params = ClientCredentialParameters.builder(scopes).build();
            IAuthenticationResult result = clientApplication.acquireToken(params).join();
            cacheIfEnabled(cacheKey, result);
            return result.accessToken();
        } catch (CompletionException e) {
            throw acquisitionFailure("Failed to acquire a client-credentials token for scopes " + scopes, e);
        }
    }

    @Override
    public String getAccessTokenOnBehalfOf(String userAssertionToken) {
        if (userAssertionToken == null || userAssertionToken.isBlank()) {
            throw new IllegalArgumentException("userAssertionToken must not be null or blank");
        }

        Set<String> scopes = scopeSet();
        // Cache key includes a hash of the assertion, never the raw token itself -
        // an OBO result is specific to the calling user, so scope alone isn't a
        // safe/correct key, but the raw assertion shouldn't be held as a cache key
        // string either (visible in heap dumps, cache introspection tools, etc.).
        String cacheKey = "obo:" + sha256(userAssertionToken) + ":" + String.join(",", scopes);

        if (properties.isCacheEnabled()) {
            String cached = tokenCache.getIfPresent(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        try {
            UserAssertion assertion = new UserAssertion(userAssertionToken);
            OnBehalfOfParameters params = OnBehalfOfParameters.builder(scopes, assertion).build();
            IAuthenticationResult result = clientApplication.acquireToken(params).join();
            cacheIfEnabled(cacheKey, result);
            return result.accessToken();
        } catch (CompletionException e) {
            // Message deliberately omits the user assertion token itself.
            throw acquisitionFailure("Failed to acquire an on-behalf-of token for scopes " + scopes, e);
        }
    }

    @Override
    public boolean validateToken(String token) {
        return tokenValidator.validate(token);
    }

    private Set<String> scopeSet() {
        return new TreeSet<>(properties.getScopes()); // sorted so the cache key is stable regardless of configured order
    }

    private void cacheIfEnabled(String cacheKey, IAuthenticationResult result) {
        if (properties.isCacheEnabled()) {
            tokenCache.put(cacheKey, result.accessToken(), result.expiresOnDate().toInstant());
        }
    }

    private TokenAcquisitionException acquisitionFailure(String message, CompletionException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        String correlationId = MDC.get(coreProperties.getMdcKey());
        log.warn("Exchange/Entra token acquisition failed [correlationId={}]: {}", correlationId, message, cause);
        return new TokenAcquisitionException(message, cause);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every standard JVM - unreachable in practice.
            throw new IllegalStateException("SHA-256 MessageDigest unavailable", e);
        }
    }
}
