package com.roconmachine.securityauth.core;

import com.roconmachine.securityauth.autoconfigure.JwtSecurityProperties;
import com.roconmachine.securityauth.extension.ClaimsCustomizer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default {@link JwtService}. jjwt's parser/builder are stateless per-call and the
 * signing {@link javax.crypto.SecretKey} supplied by {@link AuthKeyProvider} is immutable,
 * so a single instance of this class is safely shared across all request threads —
 * no synchronization is required.
 */
public class JwtServiceImpl implements JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtServiceImpl.class);

    private final JwtSecurityProperties properties;
    private final AuthKeyProvider keyProvider;
    private final List<ClaimsCustomizer> claimsCustomizers;

    public JwtServiceImpl(JwtSecurityProperties properties,
                          AuthKeyProvider keyProvider,
                           List<ClaimsCustomizer> claimsCustomizers) {
        this.properties = properties;
        this.keyProvider = keyProvider;
        this.claimsCustomizers = claimsCustomizers;
    }

    @Override
    public String generateToken(String subject, Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(properties.getExpirationMs());

        JwtBuilder builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry));

        if (extraClaims != null && !extraClaims.isEmpty()) {
            // NOTE: JwtBuilder.claims(Map) REPLACES the entire claims payload rather than
            // merging it, which would silently wipe out subject/issuer/iat/exp/jti set
            // above. Add entries individually instead, which merges.
            extraClaims.forEach(builder::claim);
        }

        for (ClaimsCustomizer customizer : claimsCustomizers) {
            customizer.customize(builder, subject);
        }

        return builder.signWith(keyProvider.getSigningKey()).compact();
    }

    @Override
    public String generateRefreshToken(String subject) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(properties.getRefreshExpirationMs());
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("token_type", "refresh")
                .signWith(keyProvider.getSigningKey())
                .compact();
    }

    @Override
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(keyProvider.getSigningKey())
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public boolean isTokenValid(String token, String expectedSubject) {
        try {
            Claims claims = parseClaims(token);
            return claims.getSubject() != null
                    && claims.getSubject().equals(expectedSubject)
                    && !isExpiredClaims(claims);
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired for subject [{}]: {}", expectedSubject, e.getMessage());
            return false;
        } catch (SignatureException e) {
            log.warn("JWT signature validation failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String extractSubject(String token) {
        return parseClaims(token).getSubject();
    }

    @Override
    public boolean isExpired(String token) {
        try {
            return isExpiredClaims(parseClaims(token));
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    private boolean isExpiredClaims(Claims claims) {
        return claims.getExpiration().before(new Date());
    }
}
