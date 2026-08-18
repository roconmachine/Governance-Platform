package com.roconmachine.governance.idempotency.endpoint;

import com.roconmachine.governance.idempotency.config.IdempotencyProperties;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.LinkedHashMap;
import java.util.Map;

@Endpoint(id = "Idempotency")
public class GovernanceIdempotencyInfoEndpoint {

    private final IdempotencyProperties properties;

    public GovernanceIdempotencyInfoEndpoint(IdempotencyProperties properties) {
        this.properties = properties;
    }

    @ReadOperation
    public Map<String, Object> idempotencyInfo() {
        Map<String, Object> idempotencyInfo = new LinkedHashMap<>();
        idempotencyInfo.put("enabled", properties.isEnabled());
        idempotencyInfo.put("urlPatterns", String.join(" ,", properties.getUrlPatterns()));
        idempotencyInfo.put("order", properties.getOrder());
        return idempotencyInfo;
    }
}