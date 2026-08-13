package com.platform.security.crypto.config;

import com.platform.security.crypto.engine.EncryptionService;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Transparent field-level encryption for JPA entities:
 *
 * <pre>{@code
 * @Convert(converter = EncryptedStringConverter.class)
 * @Column(name = "card_number")
 * private String cardNumber;
 * }</pre>
 *
 * <p><b>Why the static holder:</b> JPA providers (Hibernate included)
 * instantiate {@code AttributeConverter}s themselves via a no-arg
 * constructor - they are not Spring beans and can't have
 * {@code EncryptionService} constructor-injected. The static holder is set
 * once at application startup by {@link SecurityCryptoAutoConfiguration}
 * and is the standard, documented workaround for this exact limitation; it
 * is safe here because the held {@code EncryptionService} is itself
 * stateless apart from its (also-static-lifetime) KeyProvider.
 *
 * <p>{@code autoApply = false}: this converter is opt-in per field via
 * {@code @Convert}, not applied to every String column in the entity model -
 * encryption changes the column's on-disk format and is not something to
 * apply blanket-wide by accident.
 */
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static volatile EncryptionService encryptionService;

    /** Called once by SecurityCryptoAutoConfiguration at startup. Not part of the public API. */
    public static void initialize(EncryptionService service) {
        encryptionService = service;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        requireInitialized();
        return encryptionService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        requireInitialized();
        return encryptionService.decrypt(dbData);
    }

    private void requireInitialized() {
        if (encryptionService == null) {
            throw new IllegalStateException(
                    "EncryptedStringConverter used before SecurityCryptoAutoConfiguration " +
                            "initialized it - is security-crypto on the classpath and " +
                            "security.crypto.enabled left at its default (true)?");
        }
    }
}
