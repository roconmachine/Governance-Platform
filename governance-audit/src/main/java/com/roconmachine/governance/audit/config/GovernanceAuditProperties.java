package com.roconmachine.governance.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Audit-specific policy. Correlation id, actor resolution, and the
 * @Sensitive masking toggle are shared across every governance module and
 * live in governance-core's GovernanceCoreProperties (governance.core.*) -
 * this class only holds what's unique to auditing.
 */
@ConfigurationProperties(prefix = "governance.audit")
public class GovernanceAuditProperties {

    /** Master switch. Defaults to true - audit is opt-out, not opt-in. */
    private boolean enabled = true;

    /** Where audit events go. LOG is the safe zero-infra default; teams can supply their own AuditEventPublisher bean instead. */
    private Sink sink = Sink.LOG;

    /** Fail the call if an @Auditable method throws and the publisher itself fails? Default: no - governance must never take down the business flow. */
    private boolean failOnPublishError = false;

    /**
     * Identifies this service in every AuditEvent's serviceName field. If
     * left blank (the default), it's resolved automatically from
     * spring.application.name at startup - explicit only needed when a
     * service wants a different value in audit events than its Spring
     * application name (e.g. a shared/multi-app-name deployment).
     */
    private String serviceName;

    @NestedConfigurationProperty
    private Async async = new Async();

    public enum Sink { LOG, NOOP }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Sink getSink() { return sink; }
    public void setSink(Sink sink) { this.sink = sink; }

    public boolean isFailOnPublishError() { return failOnPublishError; }
    public void setFailOnPublishError(boolean failOnPublishError) { this.failOnPublishError = failOnPublishError; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public Async getAsync() { return async; }
    public void setAsync(Async async) { this.async = async; }

    /**
     * Bounded thread pool sizing for @Auditable(async = true) methods. Same
     * fail-safe shape as governance-exception-handling's async logging:
     * bounded queue, CallerRunsPolicy fallback under saturation (never
     * silently drops an audit event - see AuditLoggingAspect's Javadoc).
     */
    public static class Async {
        private int corePoolSize = 2;
        private int maxPoolSize = 4;
        private int queueCapacity = 500;
        private String threadNamePrefix = "governance-audit-";

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
