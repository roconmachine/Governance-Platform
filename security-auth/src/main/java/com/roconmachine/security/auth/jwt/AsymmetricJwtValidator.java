package com.roconmachine.security.auth.jwt;

import com.roconmachine.security.auth.config.SecurityAuthProperties;
import com.roconmachine.security.auth.model.AuthenticatedPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Validates JWTs signed with asymmetric keys (RSA, EC).
 *
 * Supports two modes:
 * 1. JWKS endpoint (remote): Fetches and caches public keys from an endpoint (e.g., Keycloak, Auth0)
 * 2. Static PEM key (local): Uses a PEM-encoded public key loaded at startup
 *
 * This validator is suitable for tokens issued by external identity providers.
 */
public class AsymmetricJwtValidator implements TokenValidator {

    private static final Logger logger = LoggerFactory.getLogger(AsymmetricJwtValidator.class);

    private final SecurityAuthProperties properties;
    private final Optional<JwksClient> jwksClient;
    private final Optional<PublicKey> staticPublicKey;

    public AsymmetricJwtValidator(SecurityAuthProperties properties) {
        this.properties = properties;

        // Initialize JWKS client if jwksUri is configured
        if (properties.getJwksUri() != null && !properties.getJwksUri().isBlank()) {
            this.jwksClient = Optional.of(new JwksClient(properties.getJwksUri(), properties.getJwksCacheDurationSeconds()));
            this.staticPublicKey = Optional.empty();
            logger.info("Configured AsymmetricJwtValidator with JWKS endpoint: {}", properties.getJwksUri());
        } else if (properties.getPublicKeyPem() != null && !properties.getPublicKeyPem().isBlank()) {
            this.jwksClient = Optional.empty();
            this.staticPublicKey = Optional.of(loadPublicKeyFromPem(properties.getPublicKeyPem()));
            logger.info("Configured AsymmetricJwtValidator with static PEM public key");
        } else {
            throw new IllegalStateException(
                    "AsymmetricJwtValidator requires either security.auth.jwks-uri or security.auth.public-key-pem to be configured");
        }
    }

    @Override
    public AuthenticatedPrincipal validate(String token) throws TokenValidationException {
        Claims claims;
        try {
            // Get the public key for verification
            PublicKey publicKey = getPublicKey(token);

            // Create parser with the public key
            JwtParser parser = Jwts.parser()
                    .verifyWith(publicKey)
                    .clockSkewSeconds(properties.getClockSkewSeconds())
                    .build();

            claims = parser.parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenValidationException("Token validation failed: " + e.getMessage(), e);
        }

        // Validate issuer
        if (properties.getIssuer() != null && !properties.getIssuer().isBlank()) {
            if (!properties.getIssuer().equals(claims.getIssuer())) {
                throw new TokenValidationException("Token issuer did not match expected issuer");
            }
        }

        // Validate audience
        if (properties.getAudience() != null && !properties.getAudience().isBlank()) {
            Set<String> audience = claims.getAudience();
            if (audience == null || !audience.contains(properties.getAudience())) {
                throw new TokenValidationException("Token audience did not match expected audience");
            }
        }

        return new AuthenticatedPrincipal(claims.getSubject(), extractRoles(claims), claims);
    }

    /**
     * Retrieves the public key for token verification.
     * For JWKS endpoints, extracts the 'kid' from the token header and fetches the key.
     * For static keys, returns the pre-loaded key.
     */
    private PublicKey getPublicKey(String token) throws TokenValidationException {
        if (staticPublicKey.isPresent()) {
            return staticPublicKey.get();
        }

        if (jwksClient.isPresent()) {
            // Extract kid from the JWT header without validating yet
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new TokenValidationException("Invalid JWT format");
            }

            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String kid = extractKidFromHeader(headerJson);

            if (kid == null) {
                throw new TokenValidationException("Token header does not contain 'kid' (Key ID)");
            }

            Optional<Key> key = jwksClient.get().getKey(kid);
            if (key.isEmpty()) {
                throw new TokenValidationException("Public key not found for kid: " + kid);
            }

            if (!(key.get() instanceof PublicKey publicKey)) {
                throw new TokenValidationException("Retrieved key is not a PublicKey");
            }

            return publicKey;
        }

        throw new TokenValidationException("No public key source configured");
    }

    /**
     * Extracts the 'kid' (Key ID) from the JWT header.
     * Simple string extraction without JSON parsing.
     */
    private String extractKidFromHeader(String headerJson) {
        // Simple extraction: look for "kid": "value"
        int kidIndex = headerJson.indexOf("\"kid\"");
        if (kidIndex < 0) {
            return null;
        }

        int colonIndex = headerJson.indexOf(':', kidIndex);
        int quoteStart = headerJson.indexOf('"', colonIndex);
        int quoteEnd = headerJson.indexOf('"', quoteStart + 1);

        if (quoteStart < 0 || quoteEnd < 0) {
            return null;
        }

        return headerJson.substring(quoteStart + 1, quoteEnd);
    }

    /**
     * Loads a public key from a PEM-encoded string.
     * Supports RSA and EC keys. Example format:
     * -----BEGIN PUBLIC KEY-----
     * MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
     * -----END PUBLIC KEY-----
     */
    private PublicKey loadPublicKeyFromPem(String pem) throws TokenValidationException {
        try {
            // Remove PEM headers and whitespace
            String key = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                    .replace("-----END RSA PUBLIC KEY-----", "")
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s+", "");

            byte[] decodedKey = Base64.getDecoder().decode(key);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decodedKey);

            // Try RSA first, then EC
            try {
                KeyFactory factory = KeyFactory.getInstance("RSA");
                return factory.generatePublic(spec);
            } catch (Exception e) {
                KeyFactory factory = KeyFactory.getInstance("EC");
                return factory.generatePublic(spec);
            }
        } catch (Exception e) {
            throw new TokenValidationException("Failed to load public key from PEM", e);
        }
    }

    /**
     * Extracts roles from the JWT claims.
     * Uses the configurable rolesClaim property (default: "roles").
     */
    @SuppressWarnings("unchecked")
    private Set<String> extractRoles(Claims claims) {
        Object raw = claims.get(properties.getRolesClaim());
        if (raw instanceof List<?> list) {
            Set<String> roles = new LinkedHashSet<>();
            for (Object item : list) {
                roles.add(String.valueOf(item));
            }
            return roles;
        }
        if (raw instanceof String single) {
            return Set.of(single);
        }
        return Set.of();
    }
}
