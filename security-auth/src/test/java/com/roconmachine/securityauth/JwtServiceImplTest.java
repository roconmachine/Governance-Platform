package com.roconmachine.securityauth;

import com.roconmachine.securityauth.autoconfigure.JwtSecurityProperties;
import com.roconmachine.securityauth.core.DefaultKeyProvider;
import com.roconmachine.securityauth.core.JwtService;
import com.roconmachine.securityauth.core.JwtServiceImpl;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceImplTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtSecurityProperties properties = new JwtSecurityProperties();
        properties.setSecret(Base64.getEncoder().encodeToString("a-32-byte-minimum-test-secret!!".getBytes()));
        properties.setIssuer("test-issuer");
        properties.setExpirationMs(60_000L);

        jwtService = new JwtServiceImpl(properties, new DefaultKeyProvider(properties), List.of());
    }

    @Test
    void generatesAndValidatesToken() {
        String token = jwtService.generateToken("user-123", Map.of("role", "ADMIN"));

        assertTrue(jwtService.isTokenValid(token, "user-123"));
        assertEquals("user-123", jwtService.extractSubject(token));
        assertFalse(jwtService.isExpired(token));

        Claims claims = jwtService.parseClaims(token);
        assertEquals("ADMIN", claims.get("role"));
        assertEquals("test-issuer", claims.getIssuer());
    }

    @Test
    void rejectsTokenForWrongSubject() {
        String token = jwtService.generateToken("user-123", Map.of());
        assertFalse(jwtService.isTokenValid(token, "someone-else"));
    }

    @Test
    void isThreadSafeUnderConcurrentIssuance() throws InterruptedException {
        int threads = 16;
        int tokensPerThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger failures = new AtomicInteger();

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            pool.submit(() -> {
                for (int i = 0; i < tokensPerThread; i++) {
                    String subject = "user-" + threadId + "-" + i;
                    String token = jwtService.generateToken(subject, Map.of());
                    if (!jwtService.isTokenValid(token, subject)) {
                        failures.incrementAndGet();
                    }
                }
            });
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(0, failures.get());
    }
}
