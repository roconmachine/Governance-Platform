package com.platform.security.exchange.validation;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.platform.security.exchange.config.ExchangeAuthProperties;
import com.platform.security.exchange.exception.ExchangeAuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.List;

/**
 * Validates an arbitrary Entra-issued access token: signature (against the
 * tenant's published JWKS, fetched once and cached/auto-refreshed by
 * Nimbus's {@code RemoteJWKSet}), expiry, not-before, and - if configured -
 * audience. MSAL4J itself only ACQUIRES tokens; it has no facility for
 * validating one your service received from elsewhere (e.g. the user
 * assertion token in an OBO exchange, before you trust it enough to send it
 * onward), so this is a deliberate, separate piece built on Nimbus JOSE+JWT
 * (the same library Spring Security's own OAuth2 resource-server support
 * uses under the hood).
 *
 * {@code validate()} returns a plain boolean and never throws - callers get
 * a yes/no answer, and the specific reason a token failed (expired vs. bad
 * signature vs. wrong audience) is logged server-side only at DEBUG, never
 * surfaced to a caller of this boolean method. This mirrors
 * security-auth's TokenValidationException philosophy: telling a caller
 * exactly why validation failed is free reconnaissance for an attacker.
 *
 * Thread-safe: {@code RemoteJWKSet} and {@code DefaultJWTProcessor} are both
 * safe for concurrent use, and this class holds no other mutable state.
 */
public class EntraTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(EntraTokenValidator.class);

    private final ExchangeAuthProperties properties;
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    public EntraTokenValidator(ExchangeAuthProperties properties) {
        this.properties = properties;
        this.jwtProcessor = buildProcessor(properties);
    }

    public boolean validate(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            JWTClaimsSet claims = jwtProcessor.process(token, null);
            return hasNotExpired(claims) && isNotBeforeSatisfied(claims) && audienceMatches(claims);
        } catch (Exception e) {
            // Deliberately one catch-all: ParseException, BadJOSEException, JOSEException
            // all mean the same thing to a caller of this boolean method - "not valid" -
            // and the specific cause is only useful server-side.
            log.debug("Token validation failed", e);
            return false;
        }
    }

    private boolean hasNotExpired(JWTClaimsSet claims) {
        Instant expiry = claims.getExpirationTime() == null ? null : claims.getExpirationTime().toInstant();
        if (expiry == null || expiry.isBefore(Instant.now())) {
            log.debug("Token rejected: missing or past expiration ({})", expiry);
            return false;
        }
        return true;
    }

    private boolean isNotBeforeSatisfied(JWTClaimsSet claims) {
        Instant notBefore = claims.getNotBeforeTime() == null ? null : claims.getNotBeforeTime().toInstant();
        if (notBefore != null && notBefore.isAfter(Instant.now())) {
            log.debug("Token rejected: not yet valid (nbf={})", notBefore);
            return false;
        }
        return true;
    }

    private boolean audienceMatches(JWTClaimsSet claims) throws java.text.ParseException {
        String expected = properties.getValidation().getExpectedAudience();
        if (expected == null || expected.isBlank()) {
            // No audience configured - validated signature/expiry only. See
            // ExchangeAuthProperties.Validation's Javadoc for why this is a
            // deliberately weaker default, not a silent gap.
            return true;
        }
        List<String> audience = claims.getAudience();
        boolean matches = audience != null && audience.contains(expected);
        if (!matches) {
            log.debug("Token rejected: audience {} did not contain expected value '{}'", audience, expected);
        }
        return matches;
    }

    private ConfigurableJWTProcessor<SecurityContext> buildProcessor(ExchangeAuthProperties properties) {
        try {
            String jwksUri = properties.getAuthorityHost() + properties.getTenantId() + "/discovery/v2.0/keys";
            DefaultResourceRetriever resourceRetriever = new DefaultResourceRetriever(
                    (int) properties.getConnectTimeoutMillis(), (int) properties.getReadTimeoutMillis());
            JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(new URL(jwksUri), resourceRetriever);

            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource);
            processor.setJWSKeySelector(keySelector);
            return processor;
        } catch (MalformedURLException e) {
            throw new ExchangeAuthException(
                    "Invalid JWKS URI built from exchange.auth.authority-host + tenant-id", e);
        }
    }
}
