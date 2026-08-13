package com.platform.security.exchange.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Externalizes every Entra ID / app-registration detail this module needs.
 * Bound from {@code exchange.auth.*} - see the module README for a complete
 * sample {@code application.yml}.
 */
@ConfigurationProperties(prefix = "exchange.auth")
public class ExchangeAuthProperties {

    /** Master switch. Defaults to true. */
    private boolean enabled = true;

    /** Azure/Entra Tenant ID (GUID or verified domain, e.g. "contoso.onmicrosoft.com"). Required. */
    private String tenantId;

    /** App Registration (Application) Client ID. Required. */
    private String clientId;

    /**
     * App Registration client secret. Set this OR {@link #certificate}'s
     * path, not both. A certificate is the stronger, recommended credential
     * for anything handling real data - see that nested class's Javadoc.
     */
    private String clientSecret;

    /** Default scopes requested for the Client Credentials flow, e.g. "https://graph.microsoft.com/.default". */
    private List<String> scopes = new ArrayList<>(List.of("https://graph.microsoft.com/.default"));

    /** Whether acquired tokens are cached in-memory to avoid hitting Entra's token endpoint on every call. Defaults to true. */
    private boolean cacheEnabled = true;

    /**
     * Authority host, overridable for sovereign/government clouds (e.g.
     * "https://login.microsoftonline.us/" for US Gov,
     * "https://login.partner.microsoftonline.cn/" for the 21Vianet China
     * cloud). The tenant id is appended to this to form the full authority
     * URL - never hardcode the public cloud value if your organization
     * operates in a sovereign cloud.
     */
    private String authorityHost = "https://login.microsoftonline.com/";

    /** HTTP connect timeout for calls MSAL4J/the JWKS fetcher make to Entra ID, in milliseconds. */
    private long connectTimeoutMillis = 10_000;

    /** HTTP read timeout for the same calls, in milliseconds. */
    private long readTimeoutMillis = 10_000;

    @NestedConfigurationProperty
    private Certificate certificate = new Certificate();

    @NestedConfigurationProperty
    private Cache cache = new Cache();

    @NestedConfigurationProperty
    private Validation validation = new Validation();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public List<String> getScopes() { return scopes; }
    public void setScopes(List<String> scopes) { this.scopes = scopes; }

    public boolean isCacheEnabled() { return cacheEnabled; }
    public void setCacheEnabled(boolean cacheEnabled) { this.cacheEnabled = cacheEnabled; }

    public String getAuthorityHost() { return authorityHost; }
    public void setAuthorityHost(String authorityHost) { this.authorityHost = authorityHost; }

    public long getConnectTimeoutMillis() { return connectTimeoutMillis; }
    public void setConnectTimeoutMillis(long connectTimeoutMillis) { this.connectTimeoutMillis = connectTimeoutMillis; }

    public long getReadTimeoutMillis() { return readTimeoutMillis; }
    public void setReadTimeoutMillis(long readTimeoutMillis) { this.readTimeoutMillis = readTimeoutMillis; }

    public Certificate getCertificate() { return certificate; }
    public void setCertificate(Certificate certificate) { this.certificate = certificate; }

    public Cache getCache() { return cache; }
    public void setCache(Cache cache) { this.cache = cache; }

    public Validation getValidation() { return validation; }
    public void setValidation(Validation validation) { this.validation = validation; }

    /**
     * Alternative to {@link #clientSecret} - a PKCS12 (.pfx/.p12) keystore
     * holding the app registration's client certificate and private key.
     * Preferred over a client secret for anything handling real data:
     * certificates support longer validity with better rotation tooling and
     * can't be exfiltrated as a single copy-pasteable string the way a
     * secret can.
     */
    public static class Certificate {
        /** Filesystem path to the PKCS12 keystore. */
        private String path;
        /** Keystore (and typically also private-key) password. */
        private String password;
        /** Alias of the entry to use. If omitted, the keystore's first alias is used. */
        private String alias;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
    }

    /** Token cache sizing/refresh policy. */
    public static class Cache {
        /** Maximum distinct cache entries (one per distinct scope-set, or per distinct OBO caller+scope-set). */
        private int maxSize = 500;

        /**
         * How many seconds before a token's ACTUAL expiry it's proactively
         * treated as expired and refreshed. Protects against a token expiring
         * mid-flight on a slow downstream call, or clock skew between this
         * service and Entra ID. Default 300s (5 minutes).
         */
        private long expiryBufferSeconds = 300;

        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }

        public long getExpiryBufferSeconds() { return expiryBufferSeconds; }
        public void setExpiryBufferSeconds(long expiryBufferSeconds) { this.expiryBufferSeconds = expiryBufferSeconds; }
    }

    /** Policy for {@code validateToken()}. */
    public static class Validation {
        /**
         * Expected `aud` claim - typically your own app's client id (for
         * tokens issued TO your app) or a resource identifier (e.g.
         * "https://graph.microsoft.com" for tokens meant to call Graph).
         * Left blank, audience is not checked - acceptable for a quick
         * start, but set this explicitly for anything handling real data:
         * without it, validateToken() only proves "signed by this tenant",
         * not "intended for this application".
         */
        private String expectedAudience;

        public String getExpectedAudience() { return expectedAudience; }
        public void setExpectedAudience(String expectedAudience) { this.expectedAudience = expectedAudience; }
    }
}
