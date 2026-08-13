package com.platform.governance.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The policy every governance module shares. Kept separate from each
 * each module's own properties (governance.audit.*, governance.http-logging.*)
 * so that "how do we identify a request/actor and mask sensitive data" is
 * configured once, fleet-wide, under governance.core.* - not duplicated and
 * potentially drifting between modules.
 */
@ConfigurationProperties(prefix = "governance.core")
public class GovernanceCoreProperties {

    /** Master switch for the shared correlation-id/actor filter. */
    private boolean enabled = true;

    /** Header used to propagate a request's correlation id end-to-end. */
    private String correlationIdHeader = "X-Correlation-Id";

    /** MDC key the correlation id is stored under for log pattern usage. */
    private String mdcKey = "correlationId";

    /**
     * Plain HTTP header carrying the caller's identity, e.g. a gateway- or
     * auth-proxy-injected user id / service account name. Deliberately just a
     * header - no Spring Security dependency - so every governance module
     * built on core has zero requirement on any particular auth mechanism.
     */
    private String actorHeader = "X-User-Id";

    /** MDC key the resolved actor is read from by downstream modules. */
    private String actorMdcKey = "actor";

    /** Whether @Sensitive fields/values are masked before logging/publishing, fleet-wide default. */
    private boolean maskSensitiveData = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getCorrelationIdHeader() { return correlationIdHeader; }
    public void setCorrelationIdHeader(String correlationIdHeader) { this.correlationIdHeader = correlationIdHeader; }

    public String getMdcKey() { return mdcKey; }
    public void setMdcKey(String mdcKey) { this.mdcKey = mdcKey; }

    public String getActorHeader() { return actorHeader; }
    public void setActorHeader(String actorHeader) { this.actorHeader = actorHeader; }

    public String getActorMdcKey() { return actorMdcKey; }
    public void setActorMdcKey(String actorMdcKey) { this.actorMdcKey = actorMdcKey; }

    public boolean isMaskSensitiveData() { return maskSensitiveData; }
    public void setMaskSensitiveData(boolean maskSensitiveData) { this.maskSensitiveData = maskSensitiveData; }
}
