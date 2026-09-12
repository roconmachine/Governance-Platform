package com.roconmachine.securityauth;

import com.roconmachine.securityauth.autoconfigure.JwtSecurityProperties;
import com.roconmachine.securityauth.core.DefaultKeyProvider;
import com.roconmachine.securityauth.core.DefaultRefreshTokenService;
import com.roconmachine.securityauth.core.InMemoryRefreshTokenStore;
import com.roconmachine.securityauth.core.JwtServiceImpl;
import com.roconmachine.securityauth.core.RefreshTokenService;
import com.roconmachine.securityauth.core.RefreshTokenStore;
import com.roconmachine.securityauth.core.TokenPair;
import com.roconmachine.securityauth.exception.InvalidRefreshTokenException;
import com.roconmachine.securityauth.exception.TokenReuseDetectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefreshTokenServiceTest {

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        JwtSecurityProperties properties = new JwtSecurityProperties();
        properties.setSecret(Base64.getEncoder().encodeToString("a-32-byte-minimum-test-secret!!".getBytes()));
        properties.setIssuer("test-issuer");
        properties.setExpirationMs(60_000L);
        properties.setRefreshExpirationMs(600_000L);

        var jwtService = new JwtServiceImpl(properties, new DefaultKeyProvider(properties), List.of());
        RefreshTokenStore store = new InMemoryRefreshTokenStore();
        refreshTokenService = new DefaultRefreshTokenService(properties, jwtService, store);
    }

    @Test
    void rotationIssuesFreshPair() {
        TokenPair initial = refreshTokenService.issue("user-1");
        TokenPair rotated = refreshTokenService.rotate(initial.refreshToken());

        assertNotEquals(initial.accessToken(), rotated.accessToken());
        assertNotEquals(initial.refreshToken(), rotated.refreshToken());
    }

    @Test
    void rotatedTokenCanRotateAgain() {
        TokenPair first = refreshTokenService.issue("user-1");
        TokenPair second = refreshTokenService.rotate(first.refreshToken());
        TokenPair third = refreshTokenService.rotate(second.refreshToken());

        assertNotEquals(second.refreshToken(), third.refreshToken());
    }

    @Test
    void reusingAnAlreadyRotatedTokenIsRejected() {
        TokenPair first = refreshTokenService.issue("user-1");
        refreshTokenService.rotate(first.refreshToken()); // consumes it, family now has a live successor

        assertThrows(TokenReuseDetectedException.class,
                () -> refreshTokenService.rotate(first.refreshToken()));
    }

    @Test
    void reuseRevokesTheEntireFamilyIncludingTheLiveSuccessor() {
        TokenPair first = refreshTokenService.issue("user-1");
        TokenPair second = refreshTokenService.rotate(first.refreshToken());

        assertThrows(TokenReuseDetectedException.class,
                () -> refreshTokenService.rotate(first.refreshToken()));

        // second.refreshToken() was legitimately issued and never used, but the theft
        // response must revoke the whole family, so it must be rejected too.
        assertThrows(InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotate(second.refreshToken()));
    }

    @Test
    void unknownTokenIsRejected() {
        assertThrows(InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotate("not-a-real-token"));
    }

    @Test
    void explicitRevokeInvalidatesTheSession() {
        TokenPair pair = refreshTokenService.issue("user-1");
        refreshTokenService.revoke(pair.refreshToken());

        assertThrows(InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotate(pair.refreshToken()));
    }
}
