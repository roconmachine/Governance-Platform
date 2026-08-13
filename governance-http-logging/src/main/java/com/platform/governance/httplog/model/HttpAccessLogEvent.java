package com.platform.governance.httplog.model;

import java.time.Instant;

/**
 * One governed record of an HTTP request/response pair. Kept separate from
 * AuditEvent (governance-audit) deliberately - this is wire-level
 * traffic, not a business-action record, and the two are consumed by
 * different audiences (SRE/security vs. compliance/audit).
 */
public final class HttpAccessLogEvent {

    private final String correlationId;
    private final String actor;
    private final String method;
    private final String path;
    private final String queryString;
    private final String requestParameters;
    private final int status;
    private final long durationMillis;
    private final String requestHeaders;
    private final String responseHeaders;
    private final String requestBody;
    private final String responseBody;
    private final Instant timestamp;

    public HttpAccessLogEvent(String correlationId, String actor, String method, String path,
                               String queryString, String requestParameters, int status,
                               long durationMillis, String requestHeaders, String responseHeaders,
                               String requestBody, String responseBody, Instant timestamp) {
        this.correlationId = correlationId;
        this.actor = actor;
        this.method = method;
        this.path = path;
        this.queryString = queryString;
        this.requestParameters = requestParameters;
        this.status = status;
        this.durationMillis = durationMillis;
        this.requestHeaders = requestHeaders;
        this.responseHeaders = responseHeaders;
        this.requestBody = requestBody;
        this.responseBody = responseBody;
        this.timestamp = timestamp;
    }

    public String getCorrelationId() { return correlationId; }
    public String getActor() { return actor; }
    public String getMethod() { return method; }
    public String getPath() { return path; }
    public String getQueryString() { return queryString; }
    public String getRequestParameters() { return requestParameters; }
    public int getStatus() { return status; }
    public long getDurationMillis() { return durationMillis; }
    public String getRequestHeaders() { return requestHeaders; }
    public String getResponseHeaders() { return responseHeaders; }
    public String getRequestBody() { return requestBody; }
    public String getResponseBody() { return responseBody; }
    public Instant getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "HttpAccessLogEvent{" +
                "correlationId='" + correlationId + '\'' +
                ", actor='" + actor + '\'' +
                ", method='" + method + '\'' +
                ", path='" + path + '\'' +
                (queryString != null && !queryString.isEmpty() ? ", queryString='" + queryString + '\'' : "") +
                (requestParameters != null && !requestParameters.isEmpty() ? ", requestParameters=" + requestParameters : "") +
                ", status=" + status +
                ", durationMillis=" + durationMillis +
                ", requestHeaders=" + requestHeaders +
                ", responseHeaders=" + responseHeaders +
                (requestBody != null ? ", requestBody='" + requestBody + '\'' : "") +
                (responseBody != null ? ", responseBody='" + responseBody + '\'' : "") +
                ", timestamp=" + timestamp +
                '}';
    }
}
