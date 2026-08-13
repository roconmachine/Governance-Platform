package com.roconmachine.governance.httplog.config;

import com.roconmachine.governance.core.annotation.Sensitive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;

/**
 * HTTP-logging-specific policy. Correlation id / actor / the master
 * @Sensitive masking toggle live in governance-core's GovernanceCoreProperties
 * (governance.core.*) - this class only holds what's unique to HTTP access
 * logging: which paths to skip, whether to capture bodies, size limits, and
 * which header/parameter names are themselves sensitive (Authorization,
 * Cookie, card numbers, etc. - these aren't object fields, so they can't
 * carry @Sensitive directly, hence name-based lists here).
 */
@ConfigurationProperties(prefix = "governance.http-logging")
public class GovernanceHttpLoggingProperties {

    /** Master switch. Defaults to true - opt-out, not opt-in, like the rest of the suite. */
    private boolean enabled = true;

    /** Log request/response headers. */
    private boolean includeHeaders = true;

    /** Log request/response bodies. Off by default: bodies are the highest PII/PCI risk surface. */
    private boolean includeBody = false;

    /**
     * Log the request's query string and request parameters (query string +
     * form-encoded body params, per the servlet spec's getParameterMap()).
     * On by default - query params/request params are a lower PII/PCI risk
     * surface than full bodies (most sensitive data belongs in a body or
     * header, not a URL - putting it in a URL is itself bad practice), but
     * sensitive values are still masked per sensitiveParameterNames below.
     */
    private boolean includeParams = true;

    /** Max number of body characters captured/logged, to bound log line size and memory use. */
    private int maxBodySize = 2000;

    /** Paths excluded from logging entirely, e.g. health checks and the actuator tree. */
    private List<String> excludedPaths = List.of("/actuator/**", "/health", "/favicon.ico");

    /**
     * Header names treated as sensitive and masked with the given strategy
     * regardless of includeHeaders - name-matched case-insensitively.
     */
    private List<String> sensitiveHeaders = List.of("Authorization", "Cookie", "Set-Cookie", "X-Api-Key");

    /** Masking strategy applied to any header name in sensitiveHeaders. */
    private Sensitive.MaskStrategy sensitiveHeaderStrategy = Sensitive.MaskStrategy.FULL;

    /**
     * JSON field names masked (regex, name-based) inside captured request/response
     * bodies. Raw HTTP bodies aren't annotated Java objects, so field-level
     * @Sensitive can't apply here directly - this is the name-based equivalent,
     * and the reason includeBody defaults to false: this is a best-effort net,
     * not a guarantee, for arbitrary/nested/array JSON shapes.
     */
    private List<String> sensitiveBodyFields = List.of(
            "cardNumber", "pan", "cvv", "cvv2", "pin", "password", "ssn", "nationalId", "accountNumber");

    /**
     * Query-string/request-parameter NAMES masked with sensitiveHeaderStrategy
     * whenever captured (name-matched case-insensitively) - separate from
     * sensitiveBodyFields since these are plain key=value pairs, not JSON.
     */
    private List<String> sensitiveParameterNames = List.of(
            "cardNumber", "pan", "cvv", "cvv2", "pin", "password", "token", "ssn", "accountNumber");

    /**
     * Fraction of requests actually logged, 0.0-1.0. Lets high-throughput
     * payment-processing services keep governance enabled but sample instead
     * of logging every single call. 1.0 (log everything) by default.
     */
    private double sampleRate = 1.0;

    /**
     * If true (the default), publishing the captured HttpAccessLogEvent is
     * handed off to a bounded background executor instead of running on the
     * request thread - the response is sent without waiting on whatever the
     * configured HttpAccessLogPublisher does. Set false to force synchronous
     * publishing (the event is guaranteed published before the request
     * completes) at the cost of the publish call adding to request latency.
     */
    private boolean async = true;

    @NestedConfigurationProperty
    private Async asyncPool = new Async();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isIncludeHeaders() { return includeHeaders; }
    public void setIncludeHeaders(boolean includeHeaders) { this.includeHeaders = includeHeaders; }

    public boolean isIncludeBody() { return includeBody; }
    public void setIncludeBody(boolean includeBody) { this.includeBody = includeBody; }

    public boolean isIncludeParams() { return includeParams; }
    public void setIncludeParams(boolean includeParams) { this.includeParams = includeParams; }

    public int getMaxBodySize() { return maxBodySize; }
    public void setMaxBodySize(int maxBodySize) { this.maxBodySize = maxBodySize; }

    public List<String> getExcludedPaths() { return excludedPaths; }
    public void setExcludedPaths(List<String> excludedPaths) { this.excludedPaths = excludedPaths; }

    public List<String> getSensitiveHeaders() { return sensitiveHeaders; }
    public void setSensitiveHeaders(List<String> sensitiveHeaders) { this.sensitiveHeaders = sensitiveHeaders; }

    public Sensitive.MaskStrategy getSensitiveHeaderStrategy() { return sensitiveHeaderStrategy; }
    public void setSensitiveHeaderStrategy(Sensitive.MaskStrategy sensitiveHeaderStrategy) { this.sensitiveHeaderStrategy = sensitiveHeaderStrategy; }

    public List<String> getSensitiveBodyFields() { return sensitiveBodyFields; }
    public void setSensitiveBodyFields(List<String> sensitiveBodyFields) { this.sensitiveBodyFields = sensitiveBodyFields; }

    public List<String> getSensitiveParameterNames() { return sensitiveParameterNames; }
    public void setSensitiveParameterNames(List<String> sensitiveParameterNames) { this.sensitiveParameterNames = sensitiveParameterNames; }

    public double getSampleRate() { return sampleRate; }
    public void setSampleRate(double sampleRate) { this.sampleRate = sampleRate; }

    public boolean isAsync() { return async; }
    public void setAsync(boolean async) { this.async = async; }

    public Async getAsyncPool() { return asyncPool; }
    public void setAsyncPool(Async asyncPool) { this.asyncPool = asyncPool; }

    /** Bounded thread pool sizing for async publishing - same fail-safe shape used across this platform. */
    public static class Async {
        private int corePoolSize = 2;
        private int maxPoolSize = 4;
        private int queueCapacity = 500;
        private String threadNamePrefix = "governance-http-logging-";

        public int getCorePoolSize() { return corePoolSize; }
        public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }

        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }

        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }

        public String getThreadNamePrefix() { return threadNamePrefix; }
        public void setThreadNamePrefix(String threadNamePrefix) { this.threadNamePrefix = threadNamePrefix; }
    }
}
