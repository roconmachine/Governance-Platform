package com.roconmachine.governance.response.exception;

/**
 * Raised at application startup when {@code application.response.service-id}
 * is missing or doesn't match the required 3-character format. Deliberately
 * NOT a {@link BaseAppException} - this has no eventCode/httpStatus/type of
 * its own; it's a fail-fast configuration error, the same philosophy as
 * security-crypto's EnvKeyProvider refusing to start without a key.
 */
public class InvalidServiceIdException extends RuntimeException {
    public InvalidServiceIdException(String message) {
        super(message);
    }
}
