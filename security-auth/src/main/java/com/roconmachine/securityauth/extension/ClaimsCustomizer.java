package com.roconmachine.securityauth.extension;

import io.jsonwebtoken.JwtBuilder;

/**
 * Optional extension point letting host applications inject additional claims into
 * every issued token (roles, tenant id, permission scopes, etc). Zero, one, or many
 * beans of this type may be registered — {@link com.roconmachine.securityauth.core.JwtServiceImpl}
 * applies all of them in the order Spring injects the {@code List<ClaimsCustomizer>}.
 * Use {@link org.springframework.core.annotation.Order} on implementations if sequencing
 * matters (e.g. one customizer depends on a claim another one sets).
 */
@FunctionalInterface
public interface ClaimsCustomizer {

    /**
     * Called once per token issuance, after standard claims (sub/iss/iat/exp) and any
     * caller-supplied {@code extraClaims} have been applied, but before signing.
     *
     * @param builder the in-progress token builder — call {@code .claim(name, value)} on it
     * @param subject the "sub" claim for the token being built
     */
    void customize(JwtBuilder builder, String subject);
}
