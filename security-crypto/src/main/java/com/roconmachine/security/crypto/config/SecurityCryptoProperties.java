package com.roconmachine.security.crypto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "security.crypto")
public class SecurityCryptoProperties {

    /** Master switch. Defaults to true. */
    private boolean enabled = true;

    /** Which key id is used when a call site doesn't specify one. */
    private String defaultKeyId = "default";

    /**
     * Base64-encoded key material, keyed by key id - the simplest source for
     * EnvKeyProvider. In any environment handling real data, prefer supplying
     * your own KeyProvider bean backed by KMS/Vault instead of populating
     * this map with production key material in plain application.yml.
     */
    private Map<String, String> keys = new HashMap<>();

    /** Environment variable prefix EnvKeyProvider falls back to when a key id isn't in the `keys` map. */
    private String keyEnvPrefix = "ENCRYPTION_KEY_";

    @NestedConfigurationProperty
    private EncryptedApi encryptedApi = new EncryptedApi();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getDefaultKeyId() { return defaultKeyId; }
    public void setDefaultKeyId(String defaultKeyId) { this.defaultKeyId = defaultKeyId; }

    public Map<String, String> getKeys() { return keys; }
    public void setKeys(Map<String, String> keys) { this.keys = keys; }

    public String getKeyEnvPrefix() { return keyEnvPrefix; }
    public void setKeyEnvPrefix(String keyEnvPrefix) { this.keyEnvPrefix = keyEnvPrefix; }

    public EncryptedApi getEncryptedApi() { return encryptedApi; }
    public void setEncryptedApi(EncryptedApi encryptedApi) { this.encryptedApi = encryptedApi; }

    /** Policy for the @EncryptedAPI whole-payload request/response encryption feature. */
    public static class EncryptedApi {

        /** Master switch for @EncryptedAPI specifically. Defaults to true. */
        private boolean enabled = true;

        /**
         * The header a caller sets to indicate ITS request body is encrypted
         * and needs decryption. Checked case-sensitively by name, compared
         * case-insensitively by value against headerTrueValue.
         */
        private String headerName = "X-Encrypted";

        /** The header value that means "yes, this request body is encrypted". */
        private String headerTrueValue = "true";

        /**
         * The JSON field name both directions use to carry the ciphertext -
         * a client sends {"<payloadFieldName>": "<ciphertext>"} for a
         * decrypt-requesting request, and receives the same shape back for
         * an @EncryptedAPI response.
         */
        private String payloadFieldName = "data";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getHeaderName() { return headerName; }
        public void setHeaderName(String headerName) { this.headerName = headerName; }

        public String getHeaderTrueValue() { return headerTrueValue; }
        public void setHeaderTrueValue(String headerTrueValue) { this.headerTrueValue = headerTrueValue; }

        public String getPayloadFieldName() { return payloadFieldName; }
        public void setPayloadFieldName(String payloadFieldName) { this.payloadFieldName = payloadFieldName; }
    }
}
