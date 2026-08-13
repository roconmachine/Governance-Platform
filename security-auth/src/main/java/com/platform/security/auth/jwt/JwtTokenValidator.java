package com.platform.security.auth.jwt;

import com.platform.security.auth.config.SecurityAuthProperties;
import com.platform.security.auth.model.AuthenticatedPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * HMAC-signed JWT validation (HS256/384/512, key length determines which).
 * For tokens issued by an external identity provider using asymmetric keys
 * or a JWKS endpoint, replace this bean with one built on the same
 * {@link TokenValidator} interface rather than extending this class - the
 * signature-verification mechanics differ enough (key rotation, JWKS
 * caching) that composition is clearer than inheritance here.
 */
public class JwtTokenValidator implements TokenValidator {

    private final SecurityAuthProperties properties;
    private final JwtParser parser;

    public JwtTokenValidator(SecurityAuthProperties properties) {
        this.properties = properties;
        if (properties.getHmacSecret() == null || properties.getHmacSecret().isBlank()) {
            throw new IllegalStateException(
                    "security.auth.hmac-secret is not configured - refusing to start a token " +
                            "validator with no key material rather than accepting unsigned/unverifiable tokens");
        }
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.getHmacSecret()));
        this.parser = Jwts.parser()
                .verifyWith(key)
                .clockSkewSeconds(properties.getClockSkewSeconds())
                .build();
    }

    @Override
    public AuthenticatedPrincipal validate(String token) throws TokenValidationException {
        Claims claims;
        try {
            claims = parser.parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            // Deliberately one exception type for every failure mode (expired,
            // bad signature, malformed) - see TokenValidationException's Javadoc
            // for why the distinction never reaches an HTTP response.
            throw new TokenValidationException("Token validation failed: " + e.getMessage(), e);
        }

        if (properties.getIssuer() != null && !properties.getIssuer().isBlank()
                && !properties.getIssuer().equals(claims.getIssuer())) {
            throw new TokenValidationException("Token issuer did not match expected issuer");
        }

        if (properties.getAudience() != null && !properties.getAudience().isBlank()) {
            Set<String> audience = claims.getAudience();
            if (audience == null || !audience.contains(properties.getAudience())) {
                throw new TokenValidationException("Token audience did not match expected audience");
            }
        }

        return new AuthenticatedPrincipal(claims.getSubject(), extractRoles(claims), claims);
    }

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
