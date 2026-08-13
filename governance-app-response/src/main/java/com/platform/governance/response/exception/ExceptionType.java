package com.platform.governance.response.exception;

/**
 * The single-character "Type" segment of the ServiceID-Type-EventCode
 * format (e.g. the {@code B} in {@code PAY-B-0001}). Deliberately just
 * these two values for exceptions - {@link com.platform.governance.response.model.AppResponse}
 * success responses use a separate, fixed {@code "I"} (informational) code,
 * not a third enum value here, since a success is never "thrown".
 */
public enum ExceptionType {

    /** Business/domain logic errors - the caller did something the domain rules reject (insufficient funds, duplicate request, etc.). Maps to 4xx by default. */
    BUSINESS("B"),

    /** System/infrastructure failures - a downstream dependency, database, or unexpected internal fault. Maps to 5xx by default. */
    SYSTEM("S");

    private final String code;

    ExceptionType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
