package com.platform.security.exchange.exception;

/**
 * Raised when MSAL4J fails to acquire a token - expired/invalid credentials,
 * network failure reaching Entra ID, an invalid user assertion for the OBO
 * flow, consent not granted for the requested scopes, etc. Deliberately a
 * distinct type from {@link ExchangeAuthException} (its parent, used for
 * configuration/setup failures) so calling code can catch acquisition
 * failures specifically - e.g. to retry with backoff - without also
 * catching startup misconfiguration.
 *
 * The underlying MSAL4J {@code MsalException} (with its rich diagnostic
 * fields - error codes, correlation id from Microsoft's side, claims
 * challenge for conditional access) is always preserved as the cause; this
 * exception's own message is a short, non-sensitive summary safe to log at
 * INFO/WARN without checking for leaked secrets.
 */
public class TokenAcquisitionException extends ExchangeAuthException {
    public TokenAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
