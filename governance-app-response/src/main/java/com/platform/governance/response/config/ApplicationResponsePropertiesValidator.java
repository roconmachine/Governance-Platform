package com.platform.governance.response.config;

import com.platform.governance.response.exception.InvalidServiceIdException;

import java.util.regex.Pattern;

/**
 * Invoked once at application context startup (see
 * ApplicationResponseAutoConfiguration's eagerly-initialized bean) so a
 * misconfigured or missing service-id fails the deployment immediately,
 * with a clear diagnostic - not on the first request that happens to throw
 * an exception and discovers a malformed appCode.
 */
public class ApplicationResponsePropertiesValidator {

    private static final Pattern SERVICE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9]{3}$");

    public void validate(ApplicationResponseProperties properties) {
        String serviceId = properties.getServiceId();

        if (serviceId == null || serviceId.isBlank()) {
            throw new InvalidServiceIdException(
                    "application.response.service-id is required but was not configured. " +
                            "It must be exactly 3 alphanumeric characters, e.g. \"PAY\", \"ORD\", \"101\".");
        }
        if (!SERVICE_ID_PATTERN.matcher(serviceId).matches()) {
            throw new InvalidServiceIdException(
                    "application.response.service-id '" + serviceId + "' is invalid: it must be exactly " +
                            "3 alphanumeric characters (e.g. \"PAY\", \"ORD\", \"101\"), but was " +
                            serviceId.length() + " character(s): '" + serviceId + "'.");
        }
    }
}
