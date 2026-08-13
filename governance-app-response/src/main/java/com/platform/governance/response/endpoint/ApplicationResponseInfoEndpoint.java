package com.platform.governance.response.endpoint;

import com.platform.governance.response.config.ApplicationResponseProperties;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.LinkedHashMap;
import java.util.Map;

@Endpoint(id = "appResponse")
public class ApplicationResponseInfoEndpoint {

    private final ApplicationResponseProperties properties;

    public ApplicationResponseInfoEndpoint(ApplicationResponseProperties properties) {
        this.properties = properties;
    }

    @ReadOperation
    public Map<String, Object> appResponsePolicy() {
        Map<String, Object> appResponse = new LinkedHashMap<>();
        appResponse.put("enabled", properties.isEnabled());
        appResponse.put("serviceId", properties.getServiceId());
        appResponse.put("successEventCode", properties.getSuccessEventCode());
        appResponse.put("defaultSystemEventCode", properties.getDefaultSystemEventCode());
        appResponse.put("exampleSuccessAppCode",
                properties.getServiceId() + "-I-" + properties.getSuccessEventCode());
        appResponse.put("exampleBusinessAppCode", properties.getServiceId() + "-B-0001");
        appResponse.put("exampleSystemAppCode", properties.getServiceId() + "-S-" + properties.getDefaultSystemEventCode());

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("appResponse", appResponse);
        policy.put("module", "governance-app-response:0.1.0-SNAPSHOT");
        return policy;
    }
}
