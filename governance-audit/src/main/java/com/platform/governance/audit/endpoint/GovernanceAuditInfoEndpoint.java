package com.platform.governance.audit.endpoint;

import com.platform.governance.audit.config.GovernanceAuditProperties;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes audit-specific policy at /actuator/governanceAudit. The shared
 * correlation-id/actor/masking policy is exposed separately at
 * /actuator/governance by governance-core's own endpoint - this one only
 * covers what's unique to auditing (sink, failure behavior).
 *
 * Named GovernanceAuditInfoEndpoint, not GovernanceInfoEndpoint - the
 * previous name collided with governance-core's endpoint class of the same
 * simple name, causing a Spring bean-registration conflict (bean names
 * default to the @Bean method name, which had also been named
 * governanceInfoEndpoint in both auto-configurations - independent of the
 * two classes' different @Endpoint ids, which were already correctly
 * distinct). Every other module in this platform already followed the
 * <ModuleName>InfoEndpoint convention; this class just hadn't been
 * renamed to match when it was split out of governance-core.
 */
@Endpoint(id = "governanceAudit")
public class GovernanceAuditInfoEndpoint {

    private final GovernanceAuditProperties properties;

    public GovernanceAuditInfoEndpoint(GovernanceAuditProperties properties) {
        this.properties = properties;
    }

    @ReadOperation
    public Map<String, Object> auditPolicy() {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("enabled", properties.isEnabled());
        audit.put("sink", properties.getSink());
        audit.put("failOnPublishError", properties.isFailOnPublishError());
        audit.put("serviceNameOverride", properties.getServiceName() == null || properties.getServiceName().isBlank()
                ? "(none - resolved from spring.application.name at startup)"
                : properties.getServiceName());

        Map<String, Object> async = new LinkedHashMap<>();
        async.put("corePoolSize", properties.getAsync().getCorePoolSize());
        async.put("maxPoolSize", properties.getAsync().getMaxPoolSize());
        async.put("queueCapacity", properties.getAsync().getQueueCapacity());
        async.put("threadNamePrefix", properties.getAsync().getThreadNamePrefix());
        async.put("rejectionPolicy", "CallerRuns (never drops - falls back to synchronous publishing under sustained overload)");
        audit.put("async", async);

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("audit", audit);
        policy.put("module", "governance-audit:0.1.0-SNAPSHOT");
        policy.put("seeAlso", "/actuator/governance (shared correlation-id/actor/masking policy)");
        return policy;
    }
}
