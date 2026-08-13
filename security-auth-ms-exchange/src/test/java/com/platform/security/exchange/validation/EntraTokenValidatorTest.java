package com.platform.security.exchange.validation;

import com.platform.security.exchange.config.ExchangeAuthProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deliberately limited in scope: fully exercising signature verification
 * requires either a live Entra tenant or a local JWKS stub server, which is
 * out of place in a fast unit test. What's genuinely worth verifying here
 * without network access is the fail-safe contract - malformed input is
 * rejected by returning false, never by throwing - since that's the
 * property {@link EntraTokenValidator#validate} promises callers.
 */
class EntraTokenValidatorTest {

    private EntraTokenValidator validator() {
        ExchangeAuthProperties properties = new ExchangeAuthProperties();
        properties.setTenantId("test-tenant-id");
        properties.setClientId("test-client-id");
        return new EntraTokenValidator(properties);
    }

    @Test
    void nullTokenIsRejectedWithoutThrowing() {
        assertThat(validator().validate(null)).isFalse();
    }

    @Test
    void blankTokenIsRejectedWithoutThrowing() {
        assertThat(validator().validate("   ")).isFalse();
    }

    @Test
    void malformedTokenIsRejectedWithoutThrowing() {
        // Not a syntactically valid JWT compact serialization at all - fails during
        // parsing, before any JWKS network call is attempted, so this is safe to
        // run without network access and still exercises the fail-closed contract.
        assertThat(validator().validate("this-is-not-a-jwt")).isFalse();
    }

    @Test
    void tokenWithOnlyTwoSegmentsIsRejectedWithoutThrowing() {
        assertThat(validator().validate("header.payload")).isFalse();
    }
}
