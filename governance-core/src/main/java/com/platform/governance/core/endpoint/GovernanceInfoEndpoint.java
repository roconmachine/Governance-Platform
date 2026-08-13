package com.platform.governance.core.endpoint;

import com.platform.governance.core.config.GovernanceCoreProperties;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes the shared governance policy (correlation id, actor, masking) at
 * /actuator/governance. Each governance module that adds its own policy
 * (audit, http-logging, ...) registers its own additional endpoint id
 * (e.g. /actuator/governanceAudit) rather than fighting over this one, so an
 * auditor can inspect exactly which governance modules are active and how
 * each is configured with a handful of curl calls.
 */
@Endpoint(id = "governance")
public class GovernanceInfoEndpoint {

    private final GovernanceCoreProperties properties;

    public GovernanceInfoEndpoint(GovernanceCoreProperties properties) {
        this.properties = properties;
    }

    @ReadOperation
    public Map<String, Object> governancePolicy() {
        Map<String, Object> core = new LinkedHashMap<>();
        core.put("enabled", properties.isEnabled());
        core.put("correlationIdHeader", properties.getCorrelationIdHeader());
        core.put("actorHeader", properties.getActorHeader());
        core.put("maskSensitiveData", properties.isMaskSensitiveData());

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("core", core);
        policy.put("module", "governance-core:0.1.0-SNAPSHOT");
        return policy;
    }
}
