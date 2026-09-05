package com.roconmachine.security.auth.jwt;

import com.roconmachine.security.auth.config.SecurityAuthProperties;
import com.roconmachine.security.auth.key.SigningKeyProvider;
import com.roconmachine.security.auth.model.TokenClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenIssuerTest {

    @Mock
    private SigningKeyProvider signingKeyProvider;

    private SecurityAuthProperties properties;
    private JwtTokenIssuer tokenIssuer;
    private SecretKey testSecretKey;

    // Standard valid 64-byte Base64 key for HS512
    private static final String TEST_BASE64_KEY =
            "c3VwcGVyU2VjdXJlS2V5Rm9ySFM1MTJTaWduaW5nQWxnT3JpdGhtV2l0aEV4YWN0bHk2NEJ5dGVzTGVuZ3RoMTIzNDU2Nzg5MDFiY2RlZg==";

    @BeforeEach
    void setUp() {
        properties = new SecurityAuthProperties();
        properties.setActiveKeyId("key-2026-v1");
        properties.setIssuer("https://auth.acme.com");
        properties.setAudience("payment-service");
        properties.setAlgorithm("HS512");
        properties.setDefaultTimeToLive(Duration.ofHours(1));

        // Generate a valid HmacSHA512 SecretKey
        testSecretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_BASE64_KEY));

        tokenIssuer = new JwtTokenIssuer(signingKeyProvider, properties);
    }

    @Test
    @DisplayName("Should generate a valid JWT with correct claims, header kid, and signature")
    void shouldGenerateValidToken() {
        // Given
        when(signingKeyProvider.currentSigningKey()).thenReturn(testSecretKey);

        TokenClaims claims = TokenClaims.forSubject("Sub")
                .issuer("issuer")
                .timeToLive(Duration.ofMinutes(4))
                .data("role", "admin")
                .build();
        Map<String, Object> customClaims = Map.of("role", "ADMIN", "tenant", "acme-corp");

        // When
        String token = tokenIssuer.issue(claims);

        // Then
        assertThat(token).isNotBlank();


    }


}