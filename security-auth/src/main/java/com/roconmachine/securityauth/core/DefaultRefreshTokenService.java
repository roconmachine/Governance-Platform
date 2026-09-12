package com.roconmachine.securityauth.core;

import com.roconmachine.securityauth.autoconfigure.JwtSecurityProperties;
import com.roconmachine.securityauth.exception.InvalidRefreshTokenException;
import com.roconmachine.securityauth.exception.TokenReuseDetectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Default {@link RefreshTokenService}. Each raw refresh token is a cryptographically
 * random, opaque, Base64Url-encoded string; only its SHA-256 hash is ever persisted
 * via {@link RefreshTokenStore}, so a compromised store alone cannot be used to forge
 * or replay a session.
 *
 * <p><b>Rotation:</b> every {@link #rotate} call marks the presented token used and
 * issues a brand-new one in the same {@code familyId}. A client (or attacker) that
 * tries to redeem the same refresh token twice trips reuse detection: the entire
 * family is revoked immediately, invalidating every token descended from that login,
 * and {@link TokenReuseDetectedException} is thrown so the host application can force
 * re-authentication and flag the account.
 *
 * <p>Thread-safe: holds no mutable state of its own beyond the injected
 * {@link RefreshTokenStore}, which is responsible for its own thread safety.
 */
public class DefaultRefreshTokenService implements RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(DefaultRefreshTokenService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JwtSecurityProperties properties;
    private final JwtService jwtService;
    private final RefreshTokenStore store;

    public DefaultRefreshTokenService(JwtSecurityProperties properties,
                                       JwtService jwtService,
                                       RefreshTokenStore store) {
        this.properties = properties;
        this.jwtService = jwtService;
        this.store = store;
    }

    @Override
    public TokenPair issue(String subject) {
        String familyId = UUID.randomUUID().toString();
        return issueWithinFamily(subject, familyId);
    }

    @Override
    public TokenPair rotate(String rawRefreshToken) {
        String tokenHash = hash(rawRefreshToken);

        StoredRefreshToken stored = store.findByHash(tokenHash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not recognized"));

        if (store.isFamilyRevoked(stored.familyId()) || stored.revoked()) {
            throw new InvalidRefreshTokenException("Refresh token has been revoked");
        }

        if (stored.isExpired()) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        if (stored.used()) {
            // One-time-use token presented a second time: treat as theft, nuke the family.
            log.warn("Refresh token reuse detected for subject [{}], family [{}] - revoking "
                    + "entire token family", stored.subject(), stored.familyId());
            store.revokeFamily(stored.familyId());
            throw new TokenReuseDetectedException(
                    "Refresh token reuse detected; session has been terminated", stored.subject());
        }

        store.update(stored.markUsed(Instant.now()));

        return issueWithinFamily(stored.subject(), stored.familyId());
    }

    @Override
    public void revoke(String rawRefreshToken) {
        String tokenHash = hash(rawRefreshToken);
        store.findByHash(tokenHash).ifPresent(stored -> store.revokeFamily(stored.familyId()));
    }

    private TokenPair issueWithinFamily(String subject, String familyId) {
        String accessToken = jwtService.generateToken(subject, Map.of());

        byte[] randomBytes = new byte[properties.getRefreshTokenLengthBytes()];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawRefreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        Instant now = Instant.now();
        StoredRefreshToken record = new StoredRefreshToken(
                hash(rawRefreshToken),
                familyId,
                subject,
                now,
                now.plusMillis(properties.getRefreshExpirationMs()),
                false,
                null,
                false
        );
        store.save(record);

        return new TokenPair(accessToken, rawRefreshToken);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every standard JVM; this is unreachable.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
