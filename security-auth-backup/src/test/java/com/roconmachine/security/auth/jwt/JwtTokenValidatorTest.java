package com.roconmachine.security.auth.jwt;

import com.roconmachine.security.auth.config.SecurityAuthProperties;
import com.roconmachine.security.auth.model.AuthenticatedPrincipal;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenValidatorTest {

    private String base64Secret(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    private SecurityAuthProperties propertiesWithSecret(String base64Secret) {
        SecurityAuthProperties properties = new SecurityAuthProperties();
        properties.setHmacSecret(base64Secret);
        properties.setIssuer("payment-service");
        return properties;
    }

    @Test
    void validTokenResolvesSubjectAndRoles() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        String token = Jwts.builder()
                .subject("user-123")
                .issuer("payment-service")
                .claim("roles", List.of("PAYMENT_ADMIN", "PAYMENT_VIEWER"))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key)
                .compact();

        JwtTokenValidator validator = new JwtTokenValidator(propertiesWithSecret(base64Secret(key)));
        AuthenticatedPrincipal principal = validator.validate(token);

        assertThat(principal.getSubject()).isEqualTo("user-123");
        assertThat(principal.getRoles()).containsExactlyInAnyOrder("PAYMENT_ADMIN", "PAYMENT_VIEWER");
        assertThat(principal.hasRole("PAYMENT_ADMIN")).isTrue();
        assertThat(principal.hasRole("PAYMENT_SUPERUSER")).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        String token = Jwts.builder()
                .subject("user-123")
                .issuer("payment-service")
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(key)
                .compact();

        JwtTokenValidator validator = new JwtTokenValidator(propertiesWithSecret(base64Secret(key)));

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(TokenValidationException.class);
    }

    @Test
    void tokenSignedWithWrongKeyIsRejected() {
        SecretKey correctKey = Jwts.SIG.HS256.key().build();
        SecretKey wrongKey = Jwts.SIG.HS256.key().build();

        String token = Jwts.builder()
                .subject("user-123")
                .issuer("payment-service")
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(wrongKey)
                .compact();

        JwtTokenValidator validator = new JwtTokenValidator(propertiesWithSecret(base64Secret(correctKey)));

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(TokenValidationException.class);
    }

    @Test
    void tokenWithUnexpectedIssuerIsRejected() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        String token = Jwts.builder()
                .subject("user-123")
                .issuer("some-other-service")
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key)
                .compact();

        JwtTokenValidator validator = new JwtTokenValidator(propertiesWithSecret(base64Secret(key)));

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(TokenValidationException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    void tokenWithNoRolesClaimResolvesToEmptyRoleSet() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        String token = Jwts.builder()
                .subject("user-123")
                .issuer("payment-service")
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key)
                .compact();

        JwtTokenValidator validator = new JwtTokenValidator(propertiesWithSecret(base64Secret(key)));
        AuthenticatedPrincipal principal = validator.validate(token);

        assertThat(principal.getRoles()).isEmpty();
    }

    @Test
    void constructorRefusesToStartWithoutAConfiguredSecret() {
        SecurityAuthProperties properties = new SecurityAuthProperties();
        // hmacSecret intentionally left unset
        assertThatThrownBy(() -> new JwtTokenValidator(properties))
                .isInstanceOf(IllegalStateException.class);
    }
}
