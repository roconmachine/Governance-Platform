package com.roconmachine.security.crypto.engine;

import com.roconmachine.security.crypto.config.SecurityCryptoProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvKeyProviderTest {

    @Test
    void throwsRatherThanSilentlyProceedingWhenNoKeyIsConfigured() {
        SecurityCryptoProperties properties = new SecurityCryptoProperties();
        properties.setDefaultKeyId("unconfigured-key");
        EnvKeyProvider provider = new EnvKeyProvider(properties);

        assertThatThrownBy(() -> provider.getKeyBytes("unconfigured-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unconfigured-key");
    }
}
