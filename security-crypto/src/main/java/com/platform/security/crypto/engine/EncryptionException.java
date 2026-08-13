package com.platform.security.crypto.engine;

/** Unchecked - a failed encrypt/decrypt is a hard stop, not something most call sites can meaningfully recover from. */
public class EncryptionException extends RuntimeException {
    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
