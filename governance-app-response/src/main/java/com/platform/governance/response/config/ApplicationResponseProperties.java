package com.platform.governance.response.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "application.response")
public class ApplicationResponseProperties {

    /** Master switch. Defaults to true. */
    private boolean enabled = true;

    /**
     * Exactly 3 alphanumeric characters identifying this service in every
     * appCode it produces (e.g. "PAY", "ORD", "101"). Required - validated
     * at startup by ApplicationResponsePropertiesValidator; there is no
     * sensible default, since a made-up default would silently produce
     * appCodes attributed to the wrong service.
     */
    private String serviceId;

    /** The 4-digit event code used for the "I" (informational/success) appCode segment, e.g. "PAY-I-0000". */
    private String successEventCode = "0000";

    /** The 4-digit event code used when an unhandled (non-BaseAppException) exception is caught - see GlobalAppExceptionHandler. */
    private String defaultSystemEventCode = "9999";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }

    public String getSuccessEventCode() { return successEventCode; }
    public void setSuccessEventCode(String successEventCode) { this.successEventCode = successEventCode; }

    public String getDefaultSystemEventCode() { return defaultSystemEventCode; }
    public void setDefaultSystemEventCode(String defaultSystemEventCode) { this.defaultSystemEventCode = defaultSystemEventCode; }
}
