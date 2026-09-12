package com.roconmachine.securityauth.core;

import com.roconmachine.securityauth.autoconfigure.JwtSecurityProperties;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Default {@link KeyProvider} that derives an HMAC-SHA256 {@link SecretKey} from
 * {@code jwt.security.secret}. The key is resolved once at startup and reused for the
 * lifetime of the application context, which is both thread-safe and avoids re-decoding
 * on every request.
 *
 * <p>Accepts either a Base64-encoded secret (recommended — generate one with e.g.
 * {@code openssl rand -base64 32}) or a raw UTF-8 string, provided the decoded/raw
 * bytes are at least 256 bits (32 bytes), which HMAC-SHA256 requires.
 */
public class DefaultKeyProvider implements AuthKeyProvider {

    private final SecretKey signingKey;

    public DefaultKeyProvider(JwtSecurityProperties properties) {
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.security.secret must be set (Base64-encoded, >= 256 bits) or a custom "
                            + "KeyProvider bean must be supplied to override the default one.");
        }
        this.signingKey = Keys.hmacShaKeyFor(resolveKeyBytes(secret));
    }

    private byte[] resolveKeyBytes(String secret) {
        try {
            byte[] decoded = Decoders.BASE64.decode(secret);
            if (decoded.length >= 32) {
                return decoded;
            }
        } catch (IllegalArgumentException notBase64) {
            // fall through and treat as a raw string
        }
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            throw new IllegalStateException(
                    "jwt.security.secret is too short once decoded; HMAC-SHA256 requires "
                            + ">= 256 bits (32 bytes). Generate one with: openssl rand -base64 32");
        }
        return raw;
    }

    @Override
    public SecretKey getSigningKey() {
        return signingKey;
    }
}
