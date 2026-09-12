package com.roconmachine.security.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "security.auth")
public class SecurityAuthProperties {

    /** Master switch. Defaults to true. */
    private boolean enabled = true;

    /**
     * HMAC shared secret (base64), for HS256/384/512. Set this OR
     * publicKeyPem/jwksUri, not both - HMAC is the simple zero-infra default
     * for a single-issuer internal platform; prefer an asymmetric key or a
     * JWKS endpoint when tokens are issued by a separate identity provider,
     * since HMAC requires sharing the same secret with whatever issues
     * tokens.
     */
    private String hmacSecret;

    /**
     * JWKS endpoint URI for fetching public keys (e.g., Keycloak, Auth0).
     * Keys are cached and refreshed automatically. Takes precedence over
     * publicKeyPem if both are set. Example: http://keycloak:9090/realms/myrealm/protocol/openid-connect/certs
     */
    private String jwksUri;

    /**
     * PEM-encoded RSA/EC public key for asymmetric verification.
     * Used only if jwksUri is not set. Example: -----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----
     */
    private String publicKeyPem;

    /**
     * Cache duration for JWKS keys in seconds. Only used when jwksUri is configured.
     * Defaults to 300 seconds (5 minutes).
     */
    private long jwksCacheDurationSeconds = 300;

    /** Expected issuer (`iss` claim). Tokens from any other issuer are rejected. */
    private String issuer;
    /**
     * Which entry in {@link #keys} is used to sign NEW tokens. Required -
     * refuses to start without one, same fail-closed philosophy as
     * security-crypto's EnvKeyProvider: an issuer with no designated
     * signing key is a misconfiguration, not a state to run in.
     */
    private String activeKeyId;
    /**
     * Base64-encoded HMAC key material, keyed by key id (the JWT `kid`
     * header). Keep every key a token currently in circulation might have
     * been signed with, not just activeKeyId - removing an entry here
     * before its longest-lived token would have expired anyway breaks
     * verification/refresh for tokens still out there.
     */
    private Map<String, String> keys = new HashMap<>();

    /** Expected audience (`aud` claim). Left blank to skip audience validation. */
    private String audience;

    /** Claim name holding the caller's roles - expected to be a list/array of strings. */
    private String rolesClaim = "roles";

    /** HTTP header the token is read from. */
    private String headerName = "Authorization";

    /** Prefix stripped from the header value before parsing, e.g. "Bearer ". */
    private String headerPrefix = "Bearer ";

    /** Allowed clock skew, in seconds, when checking expiry/not-before. */
    private long clockSkewSeconds = 30;
    /** Default token lifetime when a caller doesn't override it via TokenClaims.timeToLive(...). */
    private Duration defaultTimeToLive = Duration.ofHours(1);

    /** HMAC signature algorithm. HS512 by default - matches the ported library's default. */
    private String algorithm = "HS512";

    /**
     * If a token is present but fails validation, respond 401 immediately
     * (true) rather than letting the request proceed unauthenticated
     * (false). A MISSING token always proceeds unauthenticated either way -
     * this only governs the "token present but invalid" case, so public
     * endpoints on the same service are unaffected.
     */
    private boolean rejectInvalidToken = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getHmacSecret() { return hmacSecret; }
    public void setHmacSecret(String hmacSecret) { this.hmacSecret = hmacSecret; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }

    public String getRolesClaim() { return rolesClaim; }
    public void setRolesClaim(String rolesClaim) { this.rolesClaim = rolesClaim; }

    public String getHeaderName() { return headerName; }
    public void setHeaderName(String headerName) { this.headerName = headerName; }

    public String getHeaderPrefix() { return headerPrefix; }
    public void setHeaderPrefix(String headerPrefix) { this.headerPrefix = headerPrefix; }

    public long getClockSkewSeconds() { return clockSkewSeconds; }
    public void setClockSkewSeconds(long clockSkewSeconds) { this.clockSkewSeconds = clockSkewSeconds; }

    public boolean isRejectInvalidToken() { return rejectInvalidToken; }
    public void setRejectInvalidToken(boolean rejectInvalidToken) { this.rejectInvalidToken = rejectInvalidToken; }

    public String getJwksUri() { return jwksUri; }
    public void setJwksUri(String jwksUri) { this.jwksUri = jwksUri; }

    public String getPublicKeyPem() { return publicKeyPem; }
    public void setPublicKeyPem(String publicKeyPem) { this.publicKeyPem = publicKeyPem; }

    public long getJwksCacheDurationSeconds() { return jwksCacheDurationSeconds; }
    public void setJwksCacheDurationSeconds(long jwksCacheDurationSeconds) { this.jwksCacheDurationSeconds = jwksCacheDurationSeconds; }

    /**
     * Determines if asymmetric key validation should be used.
     * Returns true if jwksUri or publicKeyPem is configured.
     */
    public boolean isAsymmetricKeyConfigured() {
        return (jwksUri != null && !jwksUri.isBlank()) ||
               (publicKeyPem != null && !publicKeyPem.isBlank());
    }

    /**
     * Determines if HMAC validation should be used.
     */
    public boolean isHmacConfigured() {
        return hmacSecret != null && !hmacSecret.isBlank();
    }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    public String getActiveKeyId() { return activeKeyId; }
    public void setActiveKeyId(String activeKeyId) { this.activeKeyId = activeKeyId; }

    public Map<String, String> getKeys() { return keys; }
    public void setKeys(Map<String, String> keys) { this.keys = keys; }

    public Duration getDefaultTimeToLive() { return defaultTimeToLive; }
    public void setDefaultTimeToLive(Duration defaultTimeToLive) { this.defaultTimeToLive = defaultTimeToLive; }
}
