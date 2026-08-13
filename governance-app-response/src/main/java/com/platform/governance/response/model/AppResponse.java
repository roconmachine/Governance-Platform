package com.platform.governance.response.model;

import java.time.Instant;

/**
 * The one response shape every endpoint in a service using this module
 * returns - success or error. A client integrating against this service
 * parses exactly one envelope regardless of outcome, distinguishing
 * success/failure via {@code status} rather than by response shape.
 *
 * Immutable; built only through the {@link #success} / {@link #error}
 * factory methods below, never a public constructor, so every instance is
 * guaranteed to have a well-formed appCode and a non-null timestamp.
 */
public final class AppResponse<T> {

    private final String status; // "SUCCESS" or "ERROR"
    private final int httpCode;
    private final String appCode; // "ServiceID-Type-EventCode", e.g. "PAY-B-0001" or "PAY-I-0000"
    private final String message;
    private final T data;
    private final Instant timestamp;
    private final String traceId;

    private AppResponse(String status, int httpCode, String appCode, String message, T data, String traceId) {
        this.status = status;
        this.httpCode = httpCode;
        this.appCode = appCode;
        this.message = message;
        this.data = data;
        this.timestamp = Instant.now();
        this.traceId = traceId;
    }

    public static <T> AppResponse<T> success(int httpCode, String appCode, String message, T data, String traceId) {
        return new AppResponse<>("SUCCESS", httpCode, appCode, message, data, traceId);
    }

    public static <T> AppResponse<T> error(int httpCode, String appCode, String message, Object details, String traceId) {
        @SuppressWarnings("unchecked")
        T detailsAsData = (T) details; // error responses carry `details` (if any) in the same `data` slot as success responses
        return new AppResponse<>("ERROR", httpCode, appCode, message, detailsAsData, traceId);
    }

    public String getStatus() { return status; }
    public int getHttpCode() { return httpCode; }
    public String getAppCode() { return appCode; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public Instant getTimestamp() { return timestamp; }
    public String getTraceId() { return traceId; }
}
