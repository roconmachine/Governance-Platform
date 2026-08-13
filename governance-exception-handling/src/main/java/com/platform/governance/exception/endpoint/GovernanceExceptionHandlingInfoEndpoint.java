package com.platform.governance.exception.endpoint;

import com.platform.governance.exception.config.GovernanceExceptionHandlingProperties;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.LinkedHashMap;
import java.util.Map;

@Endpoint(id = "governanceExceptionHandling")
public class GovernanceExceptionHandlingInfoEndpoint {

    private final GovernanceExceptionHandlingProperties properties;

    public GovernanceExceptionHandlingInfoEndpoint(GovernanceExceptionHandlingProperties properties) {
        this.properties = properties;
    }

    @ReadOperation
    public Map<String, Object> exceptionHandlingPolicy() {
        Map<String, Object> asyncPool = new LinkedHashMap<>();
        asyncPool.put("corePoolSize", properties.getAsyncPool().getCorePoolSize());
        asyncPool.put("maxPoolSize", properties.getAsyncPool().getMaxPoolSize());
        asyncPool.put("queueCapacity", properties.getAsyncPool().getQueueCapacity());
        asyncPool.put("threadNamePrefix", properties.getAsyncPool().getThreadNamePrefix());
        asyncPool.put("rejectionPolicy", "CallerRuns (never drops - falls back to synchronous logging under sustained overload)");

        Map<String, Object> exceptionHandling = new LinkedHashMap<>();
        exceptionHandling.put("enabled", properties.isEnabled());
        exceptionHandling.put("defaultErrorCode", properties.getDefaultErrorCode());
        exceptionHandling.put("includeStackTraceInResponse", properties.isIncludeStackTraceInResponse());
        exceptionHandling.put("async", properties.isAsync());
        exceptionHandling.put("asyncPool", asyncPool);

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("exceptionHandling", exceptionHandling);
        policy.put("module", "governance-exception-handling:0.1.0-SNAPSHOT");
        return policy;
    }
}
