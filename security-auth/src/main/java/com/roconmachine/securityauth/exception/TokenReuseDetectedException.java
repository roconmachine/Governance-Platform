package com.roconmachine.securityauth.exception;

/**
 * Raised when an already-rotated (one-time-use) refresh token is presented again.
 * Because legitimate clients always discard a refresh token the moment it's rotated,
 * a second use of the same token means it was very likely stolen and used by both the
 * legitimate client and an attacker in a race, or solely by an attacker after the
 * legitimate client already rotated it.
 *
 * <p>By the time this exception is thrown, {@code RefreshTokenService} has already
 * revoked the entire token family server-side — the host application should catch
 * this, force the user to re-authenticate, and may want to alert the user/security
 * team of possible account compromise.
 */
public class TokenReuseDetectedException extends RuntimeException {

    private final String subject;

    public TokenReuseDetectedException(String message, String subject) {
        super(message);
        this.subject = subject;
    }

    /** The subject whose entire refresh-token family was just revoked. */
    public String getSubject() {
        return subject;
    }
}
