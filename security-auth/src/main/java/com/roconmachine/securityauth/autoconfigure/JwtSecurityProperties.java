package com.roconmachine.securityauth.autoconfigure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe binding for all {@code jwt.security.*} properties. Every field has a
 * sensible default so the starter works out-of-the-box in a dev profile; {@code secret}
 * is the only value hosts must supply for anything beyond local prototyping (or they
 * can override {@link com.roconmachine.securityauth.core.KeyProvider} entirely).
 */
@Validated
@ConfigurationProperties(prefix = "jwt.security")
public class JwtSecurityProperties {

    /** Master switch for the whole starter's auto-configuration. */
    private boolean enabled = true;

    /**
     * Symmetric HMAC-SHA256 secret. Accepts a Base64-encoded value (recommended) or a
     * raw UTF-8 string, either decoding to at least 256 bits (32 bytes). Required unless
     * a custom {@link com.roconmachine.securityauth.core.KeyProvider} bean is supplied.
     */
    private String secret;

    /** Access-token time-to-live in milliseconds. Defaults to 1 hour. */
    @Positive
    private long expirationMs = 3_600_000L;

    /** Refresh-token time-to-live in milliseconds. Defaults to 7 days. */
    @Positive
    private long refreshExpirationMs = 604_800_000L;

    /** Issuer ("iss") claim written to every token and enforced on parse. */
    @NotBlank
    private String issuer = "roconmachine";

    /** HTTP header the filter reads the bearer token from. */
    @NotBlank
    private String headerName = "Authorization";

    /** Prefix stripped from the header value before parsing. */
    @NotBlank
    private String headerPrefix = "Bearer ";

    /** Ant-style path patterns the {@code JwtAuthenticationFilter} skips entirely. */
    private String[] permitAllPatterns = {"/actuator/health/**", "/auth/**"};

    /**
     * Reserved for async claim customization / user-lookup execution off the request
     * thread. Host {@code ClaimsCustomizer} / {@code UserAuthenticationProvider} beans
     * can branch on this flag if they perform I/O.
     */
    private boolean asyncEnabled = false;

    /**
     * When true (default) and no other {@code SecurityFilterChain} bean exists, the
     * starter registers a stateless, JWT-protected default chain.
     */
    private boolean autoSecurityChainEnabled = true;

    /**
     * Enables one-time-use refresh token rotation with reuse (theft) detection via
     * {@code RefreshTokenService}. When false, {@code generateRefreshToken} on
     * {@code JwtService} still works as a plain stateless JWT, but rotation/reuse
     * tracking is unavailable.
     */
    private boolean refreshTokenRotationEnabled = true;

    /** Number of random bytes in each opaque refresh token before Base64Url-encoding. */
    @Positive
    private int refreshTokenLengthBytes = 32;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    public void setRefreshExpirationMs(long refreshExpirationMs) {
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getHeaderPrefix() {
        return headerPrefix;
    }

    public void setHeaderPrefix(String headerPrefix) {
        this.headerPrefix = headerPrefix;
    }

    public String[] getPermitAllPatterns() {
        return permitAllPatterns;
    }

    public void setPermitAllPatterns(String[] permitAllPatterns) {
        this.permitAllPatterns = permitAllPatterns;
    }

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    public void setAsyncEnabled(boolean asyncEnabled) {
        this.asyncEnabled = asyncEnabled;
    }

    public boolean isAutoSecurityChainEnabled() {
        return autoSecurityChainEnabled;
    }

    public void setAutoSecurityChainEnabled(boolean autoSecurityChainEnabled) {
        this.autoSecurityChainEnabled = autoSecurityChainEnabled;
    }

    public boolean isRefreshTokenRotationEnabled() {
        return refreshTokenRotationEnabled;
    }

    public void setRefreshTokenRotationEnabled(boolean refreshTokenRotationEnabled) {
        this.refreshTokenRotationEnabled = refreshTokenRotationEnabled;
    }

    public int getRefreshTokenLengthBytes() {
        return refreshTokenLengthBytes;
    }

    public void setRefreshTokenLengthBytes(int refreshTokenLengthBytes) {
        this.refreshTokenLengthBytes = refreshTokenLengthBytes;
    }
}
