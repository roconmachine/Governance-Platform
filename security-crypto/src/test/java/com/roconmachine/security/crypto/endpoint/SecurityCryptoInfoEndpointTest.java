package com.roconmachine.security.crypto.endpoint;

import com.roconmachine.security.crypto.config.SecurityCryptoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityCryptoInfoEndpointTest {

    @Mock
    private SecurityCryptoProperties properties;

    private SecurityCryptoInfoEndpoint endpoint;

    @BeforeEach
    void setUp() {
        endpoint = new SecurityCryptoInfoEndpoint(properties);
    }

    @Test
    @DisplayName("encryptionPolicy should return correctly structured metadata")
    @SuppressWarnings("unchecked")
    void encryptionPolicy_returnsCorrectMetadataStructure() {
        // Given
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getDefaultKeyId()).thenReturn("key-v1");
        when(properties.getKeyEnvPrefix()).thenReturn("SECURITY_KEY_");

        Map<String, String> keysMap = Map.of(
                "key-v1", "secret-value-1",
                "key-v2", "secret-value-2"
        );
        when(properties.getKeys()).thenReturn(keysMap);

        // When
        Map<String, Object> response = endpoint.encryptionPolicy();

        // Then
        assertThat(response)
                .isNotNull()
                .containsEntry("module", "security-crypto")
                .containsKey("encryption");

        Map<String, Object> encryptionDetails = (Map<String, Object>) response.get("encryption");

        assertThat(encryptionDetails)
                .containsEntry("enabled", true)
                .containsEntry("algorithm", "AES/GCM/NoPadding")
                .containsEntry("defaultKeyId", "key-v1")
                .containsEntry("keyEnvPrefix", "SECURITY_KEY_");

        // Verify key IDs are exposed, but NOT secret values
        Set<String> configuredKeyIds = (Set<String>) encryptionDetails.get("configuredKeyIds");
        assertThat(configuredKeyIds).containsExactlyInAnyOrder("key-v1", "key-v2");
        assertThat(encryptionDetails.values()).doesNotContain("secret-value-1", "secret-value-2");
    }

    @Test
    @DisplayName("encryptionPolicy should handle disabled status and empty key map cleanly")
    @SuppressWarnings("unchecked")
    void encryptionPolicy_handlesDisabledAndEmptyKeys() {
        // Given
        when(properties.isEnabled()).thenReturn(false);
        when(properties.getDefaultKeyId()).thenReturn(null);
        when(properties.getKeyEnvPrefix()).thenReturn("SECURITY_KEY_");
        when(properties.getKeys()).thenReturn(Collections.emptyMap());

        // When
        Map<String, Object> response = endpoint.encryptionPolicy();

        // Then
        Map<String, Object> encryptionDetails = (Map<String, Object>) response.get("encryption");

        assertThat(encryptionDetails)
                .containsEntry("enabled", false)
                .containsEntry("defaultKeyId", null);

        Set<String> configuredKeyIds = (Set<String>) encryptionDetails.get("configuredKeyIds");
        assertThat(configuredKeyIds).isEmpty();
    }
}