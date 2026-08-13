package com.platform.governance.httplog.endpoint;

import com.platform.governance.httplog.config.GovernanceHttpLoggingProperties;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.LinkedHashMap;
import java.util.Map;

@Endpoint(id = "governanceHttpLogging")
public class GovernanceHttpLoggingInfoEndpoint {

    private final GovernanceHttpLoggingProperties properties;

    public GovernanceHttpLoggingInfoEndpoint(GovernanceHttpLoggingProperties properties) {
        this.properties = properties;
    }

    @ReadOperation
    public Map<String, Object> httpLoggingPolicy() {
        Map<String, Object> httpLogging = new LinkedHashMap<>();
        httpLogging.put("enabled", properties.isEnabled());
        httpLogging.put("includeHeaders", properties.isIncludeHeaders());
        httpLogging.put("includeBody", properties.isIncludeBody());
        httpLogging.put("includeParams", properties.isIncludeParams());
        httpLogging.put("maxBodySize", properties.getMaxBodySize());
        httpLogging.put("excludedPaths", properties.getExcludedPaths());
        httpLogging.put("sensitiveHeaders", properties.getSensitiveHeaders());
        httpLogging.put("sensitiveBodyFields", properties.getSensitiveBodyFields());
        httpLogging.put("sensitiveParameterNames", properties.getSensitiveParameterNames());
        httpLogging.put("sampleRate", properties.getSampleRate());
        httpLogging.put("async", properties.isAsync());

        Map<String, Object> asyncPool = new LinkedHashMap<>();
        asyncPool.put("corePoolSize", properties.getAsyncPool().getCorePoolSize());
        asyncPool.put("maxPoolSize", properties.getAsyncPool().getMaxPoolSize());
        asyncPool.put("queueCapacity", properties.getAsyncPool().getQueueCapacity());
        httpLogging.put("asyncPool", asyncPool);

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("httpLogging", httpLogging);
        policy.put("module", "governance-http-logging:0.1.0-SNAPSHOT");
        policy.put("seeAlso", "/actuator/governance (shared correlation-id/actor/masking policy)");
        return policy;
    }
}
