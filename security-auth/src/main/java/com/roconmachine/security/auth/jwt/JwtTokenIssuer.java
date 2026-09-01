package com.roconmachine.security.auth.jwt;

import com.roconmachine.security.auth.config.SecurityAuthProperties;
import com.roconmachine.security.auth.model.TokenClaims;
import com.roconmachine.security.auth.model.TokenIssuerException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.security.MacAlgorithm;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import com.roconmachine.security.auth.key.*;

public class JwtTokenIssuer implements TokenIssuer {

    private final com.roconmachine.security.auth.key.SigningKeyProvider keyProvider;
    private final SecurityAuthProperties properties;
    private final Locator<Key> keyLocator;

    public JwtTokenIssuer(SigningKeyProvider keyProvider, SecurityAuthProperties properties) {
        this.keyProvider = keyProvider;
        this.properties = properties;
        // Resolves the verification key from EACH token's own `kid` header,
        // not just the currently-active signing key - this is what lets a
        // token signed with a since-rotated-out key still be verified
        // (and refreshed onto the current key) as long as that old key
        // remains in security.token-issuer.keys.
        this.keyLocator = header -> {
            String kid = header.get("kid").toString();
            if (kid == null || kid.isBlank()) {
                throw new TokenIssuerException("Token is missing a 'kid' header - cannot determine which key it was signed with");
            }
            return keyProvider.resolveKey(kid);
        };
    }

    @Override
    public String issue(TokenClaims claims) {
        Instant now = Instant.now();
        Duration ttl = claims.getTimeToLiveOverride() != null ? claims.getTimeToLiveOverride() : properties.getDefaultTimeToLive();
        String issuer = claims.getIssuerOverride() != null ? claims.getIssuerOverride() : properties.getIssuer();

        Map<String, Object> allClaims = new LinkedHashMap<>(claims.getData());
        allClaims.put(Claims.SUBJECT, claims.getSubject());
        allClaims.put(Claims.ISSUER, issuer);
        allClaims.put(Claims.ISSUED_AT, Date.from(now));
        allClaims.put(Claims.EXPIRATION, Date.from(now.plus(ttl)));

        try {
            String keyId = keyProvider.currentKeyId();
            return Jwts.builder()
                    .claims(allClaims)
                    .header().keyId(keyId).and()
                    .signWith(keyProvider.currentSigningKey(), resolveAlgorithm())
                    .compact();
        } catch (RuntimeException e) {
            throw new TokenIssuerException("Failed to issue token for subject '" + claims.getSubject() + "'", e);
        }
    }

    @Override
    public String refresh(String existingToken, Duration newTimeToLive) {
        Claims existingClaims;
        try {
            existingClaims = Jwts.parser()
                    .keyLocator(keyLocator)
                    .build()
                    .parseSignedClaims(existingToken)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            // Deliberately one exception type/generic message for every failure mode
            // (expired, bad signature, unknown kid, malformed) - see security-auth's
            // TokenValidationException for the same don't-help-an-attacker-probe philosophy.
            throw new TokenIssuerException("Existing token is invalid and cannot be refreshed", e);
        }

        Instant now = Instant.now();
        Map<String, Object> refreshedClaims = new LinkedHashMap<>(existingClaims);
        refreshedClaims.put(Claims.ISSUED_AT, Date.from(now));
        refreshedClaims.put(Claims.EXPIRATION, Date.from(now.plus(newTimeToLive)));

        try {
            // Always re-signs with the CURRENT active key, even if the
            // original token used a since-rotated-out one - each refresh
            // gradually migrates a caller's tokens onto the current key.
            String keyId = keyProvider.currentKeyId();
            return Jwts.builder()
                    .claims(refreshedClaims)
                    .header().keyId(keyId).and()
                    .signWith(keyProvider.currentSigningKey(), resolveAlgorithm())
                    .compact();
        } catch (RuntimeException e) {
            throw new TokenIssuerException("Failed to re-sign refreshed token", e);
        }
    }

    private MacAlgorithm resolveAlgorithm() {
        String algorithm = properties.getAlgorithm();
        return switch (algorithm) {
            case "HS256" -> Jwts.SIG.HS256;
            case "HS384" -> Jwts.SIG.HS384;
            case "HS512" -> Jwts.SIG.HS512;
            default -> throw new TokenIssuerException(
                    "Unsupported security.token-issuer.algorithm '" + algorithm + "' - supported: HS256, HS384, HS512");
        };
    }
}
