package com.platform.security.crypto.annotation;

import java.lang.annotation.*;

/**
 * Marks a field as requiring encryption at rest. This is primarily
 * documentation/intent - the actual encryption on JPA entities is applied by
 * putting {@code @Convert(converter = EncryptedStringConverter.class)} on the
 * field (JPA doesn't let a custom annotation trigger a converter on its own).
 * Combining both is recommended:
 *
 * <pre>{@code
 * @Encrypted
 * @Convert(converter = EncryptedStringConverter.class)
 * @Column(name = "card_number")
 * private String cardNumber;
 * }</pre>
 *
 * The extra {@code @Encrypted} marker means an ArchUnit rule (see
 * governance-archunit) can later assert "every column matching *cardNumber*,
 * *nationalId*, etc. must be annotated @Encrypted" - i.e. governance that the
 * encryption policy is actually applied, not just available.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface Encrypted {

    /**
     * Which key to use, resolved by the configured KeyProvider. Defaults to
     * the provider's default key - set this explicitly when a field must use
     * a specific key (e.g. a key scoped to one data residency region).
     */
    String keyId() default "";
}
