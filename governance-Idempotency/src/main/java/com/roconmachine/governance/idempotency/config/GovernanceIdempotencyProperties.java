package com.roconmachine.governance.idempotency.config;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "governance.idempotency")
public class IdempotencyProperties {

    /**
     * Toggles idempotency filter registration.
     */
    private boolean enabled = true;

    /**
     * URL patterns targeted by the Idempotency Filter. Defaults to API routes.
     */
    private List<String> urlPatterns = List.of("/api/*", "/v1/*");

    /**
     * Order of the FilterRegistrationBean within the Spring Security / Web filter chain.
     */
    private int order = 1;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getUrlPatterns() {
        return urlPatterns;
    }

    public void setUrlPatterns(List<String> urlPatterns) {
        this.urlPatterns = urlPatterns;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}