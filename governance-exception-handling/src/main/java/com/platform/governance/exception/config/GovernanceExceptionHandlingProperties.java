package com.platform.governance.exception.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "governance.exception-handling")
public class GovernanceExceptionHandlingProperties {

    /** Master switch. Defaults to true. */
    private boolean enabled = true;

    /** Fallback error code used when a handler doesn't have a more specific one (e.g. the generic 500 handler). */
    private String defaultErrorCode = "INTERNAL_ERROR";

    /**
     * Whether ErrorResponse.debugDetails (the exception's stack trace) is
     * included in the HTTP response body. Defaults to false - turn this on
     * ONLY in a local/dev profile. Leaving it on anywhere handling real
     * traffic hands callers internal implementation details for free.
     */
    private boolean includeStackTraceInResponse = false;

    /**
     * If true (the default - this module's original, only behavior),
     * publishing an ExceptionEvent is handed off to a bounded background
     * executor instead of the calling thread. Set false to force
     * synchronous publishing: the event is guaranteed published before the
     * error response is returned, at the cost of adding the publish call to
     * response latency.
     */
    private boolean async = true;

    @NestedConfigurationProperty
    private Async asyncPool = new Async();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getDefaultErrorCode() { return defaultErrorCode; }
    public void setDefaultErrorCode(String defaultErrorCode) { this.defaultErrorCode = defaultErrorCode; }

    public boolean isIncludeStackTraceInResponse() { return includeStackTraceInResponse; }
    public void setIncludeStackTraceInResponse(boolean includeStackTraceInResponse) { this.includeStackTraceInResponse = includeStackTraceInResponse; }

    public boolean isAsync() { return async; }
    public void setAsync(boolean async) { this.async = async; }

    public Async getAsyncPool() { return asyncPool; }
    public void setAsyncPool(Async asyncPool) { this.asyncPool = asyncPool; }

    /** Bounded thread pool sizing for async exception logging. */
    public static class Async {

        /** Threads kept alive even when idle. */
        private int corePoolSize = 2;

        /** Upper bound on threads under load. */
        private int maxPoolSize = 4;

        /**
         * Bounded queue depth. When full, the CallerRunsPolicy rejection
         * handler makes the REQUEST thread log synchronously as a fallback -
         * deliberately never silently dropping an exception log, only
         * trading "async" for "synchronous" under extreme, sustained load.
         */
        private int queueCapacity = 500;

        private String threadNamePrefix = "governance-exception-";

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
