package com.platform.security.issuer.model;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What you're asking {@code TokenIssuer} to mint. Deliberately a plain,
 * framework-agnostic builder - mirrors the shape of the ported library's
 * {@code JWTPayload}, but immutable and without the JPA-adjacent
 * {@code Entity} coupling that class had.
 */
public final class TokenClaims {

    private final String subject;
    private final String issuerOverride;
    private final Duration timeToLiveOverride;
    private final Map<String, Object> data;

    private TokenClaims(Builder builder) {
        this.subject = builder.subject;
        this.issuerOverride = builder.issuerOverride;
        this.timeToLiveOverride = builder.timeToLiveOverride;
        this.data = new LinkedHashMap<>(builder.data);
    }

    public String getSubject() { return subject; }
    public String getIssuerOverride() { return issuerOverride; }
    public Duration getTimeToLiveOverride() { return timeToLiveOverride; }
    public Map<String, Object> getData() { return data; }

    public static Builder forSubject(String subject) {
        return new Builder(subject);
    }

    public static final class Builder {
        private final String subject;
        private String issuerOverride;
        private Duration timeToLiveOverride;
        private final Map<String, Object> data = new LinkedHashMap<>();

        private Builder(String subject) {
            if (subject == null || subject.isBlank()) {
                throw new IllegalArgumentException("subject must not be null or blank");
            }
            this.subject = subject;
        }

        public Builder issuer(String issuer) {
            this.issuerOverride = issuer;
            return this;
        }

        public Builder timeToLive(Duration timeToLive) {
            this.timeToLiveOverride = timeToLive;
            return this;
        }

        public Builder data(String key, Object value) {
            this.data.put(key, value);
            return this;
        }

        public TokenClaims build() {
            return new TokenClaims(this);
        }
    }
}
