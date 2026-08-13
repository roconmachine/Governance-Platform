package com.platform.governance.response.model;

/**
 * The one place the {@code ServiceID-Type-EventCode} string is assembled -
 * every appCode in every response, success or error, is built here, so the
 * format can never drift between call sites.
 */
public final class AppCodeFormatter {

    /** Type segment used for success responses - not one of ExceptionType's values, since a success is never "thrown". */
    public static final String INFO_TYPE = "I";

    private AppCodeFormatter() {
    }

    public static String format(String serviceId, String typeCode, String eventCode) {
        return serviceId + "-" + typeCode + "-" + eventCode;
    }
}
