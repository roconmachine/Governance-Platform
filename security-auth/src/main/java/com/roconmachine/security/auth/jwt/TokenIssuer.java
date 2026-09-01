package com.platform.security.issuer;

import com.platform.security.issuer.model.TokenClaims;
import com.platform.security.issuer.model.TokenIssuerException;

import java.time.Duration;

/**
 * Mints and refreshes this platform's own signed JWTs. The counterpart to
 * security-auth's {@code TokenValidator}, which only ever verifies a token
 * someone else issued - this interface is for services that need to BE the
 * issuer (an internal auth/login endpoint, service-to-service tokens signed
 * by your own platform, local/test token minting without a real IdP).
 */
public interface TokenIssuer {

    /**
     * Mints a new token for the given claims, signed with the currently
     * active key (see security.token-issuer.active-key-id).
     *
     * @throws TokenIssuerException if signing fails
     */
    String issue(TokenClaims claims);

    /**
     * Re-signs an existing token's claims with a new expiry, without the
     * caller having to reconstruct the original TokenClaims. Verifies the
     * existing token first (using whichever key its `kid` header names,
     * even if that key has since been rotated out as the active signing
     * key) - a token that fails verification cannot be refreshed.
     *
     * @throws TokenIssuerException if the existing token is invalid/expired beyond any grace period, or re-signing fails
     */
    String refresh(String existingToken, Duration newTimeToLive);
}
