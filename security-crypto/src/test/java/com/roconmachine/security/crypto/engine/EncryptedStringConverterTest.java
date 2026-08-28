package com.roconmachine.security.crypto.engine;

import com.roconmachine.security.crypto.config.EncryptedStringConverter;
import com.roconmachine.security.crypto.engine.EncryptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncryptedStringConverterTest {

    @Mock
    private EncryptionService encryptionService;

    private EncryptedStringConverter converter;

    @BeforeEach
    void setUp() {
        converter = new EncryptedStringConverter();
        // Reset static state prior to each test run
        EncryptedStringConverter.initialize(null);
    }

    @AfterEach
    void tearDown() {
        // Clear static state after each test to prevent leaking into other test classes
        EncryptedStringConverter.initialize(null);
    }

    @Test
    @DisplayName("convertToDatabaseColumn should delegate to EncryptionService when initialized")
    void convertToDatabaseColumn_success() {
        // Given
        EncryptedStringConverter.initialize(encryptionService);
        String rawAttribute = "mySecretPassword123";
        String encryptedData = "enc_v1_xyz789";

        when(encryptionService.encrypt(rawAttribute)).thenReturn(encryptedData);

        // When
        String result = converter.convertToDatabaseColumn(rawAttribute);

        // Then
        assertThat(result).isEqualTo(encryptedData);
        verify(encryptionService).encrypt(rawAttribute);
    }

    @Test
    @DisplayName("convertToEntityAttribute should delegate to EncryptionService when initialized")
    void convertToEntityAttribute_success() {
        // Given
        EncryptedStringConverter.initialize(encryptionService);
        String encryptedData = "enc_v1_xyz789";
        String rawAttribute = "mySecretPassword123";

        when(encryptionService.decrypt(encryptedData)).thenReturn(rawAttribute);

        // When
        String result = converter.convertToEntityAttribute(encryptedData);

        // Then
        assertThat(result).isEqualTo(rawAttribute);
        verify(encryptionService).decrypt(encryptedData);
    }

    @Test
    @DisplayName("convertToDatabaseColumn should throw IllegalStateException when encryptionService is null")
    void convertToDatabaseColumn_throwsException_whenNotInitialized() {
        // Given (encryptionService is null via setUp)

        // When / Then
        assertThatThrownBy(() -> converter.convertToDatabaseColumn("rawString"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EncryptedStringConverter used before SecurityCryptoAutoConfiguration initialized it");
    }

    @Test
    @DisplayName("convertToEntityAttribute should throw IllegalStateException when encryptionService is null")
    void convertToEntityAttribute_throwsException_whenNotInitialized() {
        // Given (encryptionService is null via setUp)

        // When / Then
        assertThatThrownBy(() -> converter.convertToEntityAttribute("encryptedString"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EncryptedStringConverter used before SecurityCryptoAutoConfiguration initialized it");
    }

    @Test
    @DisplayName("initialize should update static encryptionService instance")
    void initialize_setsServiceSuccessfully() {
        // Given
        EncryptedStringConverter.initialize(encryptionService);
        when(encryptionService.encrypt("test")).thenReturn("enc_test");

        // When
        String result = converter.convertToDatabaseColumn("test");

        // Then
        assertThat(result).isEqualTo("enc_test");
    }
}
