package com.platform.governance.exception.model;

import java.time.Instant;

/**
 * What actually gets logged - internal-only, never returned to a caller.
 * Unlike {@link ErrorResponse}, this carries the full stack trace and
 * exception class name; the split exists specifically so "what we log" and
 * "what we tell the caller" can never accidentally become the same object.
 */
public final class ExceptionEvent {

    private final Instant timestamp;
    private final String correlationId;
    private final String actor;
    private final String exceptionClass;
    private final String message;
    private final String path;
    private final String httpMethod;
    private final int status;
    private final String stackTrace;

    public ExceptionEvent(Instant timestamp, String correlationId, String actor, String exceptionClass,
                           String message, String path, String httpMethod, int status, String stackTrace) {
        this.timestamp = timestamp;
        this.correlationId = correlationId;
        this.actor = actor;
        this.exceptionClass = exceptionClass;
        this.message = message;
        this.path = path;
        this.httpMethod = httpMethod;
        this.status = status;
        this.stackTrace = stackTrace;
    }

    public Instant getTimestamp() { return timestamp; }
    public String getCorrelationId() { return correlationId; }
    public String getActor() { return actor; }
    public String getExceptionClass() { return exceptionClass; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
    public String getHttpMethod() { return httpMethod; }
    public int getStatus() { return status; }
    public String getStackTrace() { return stackTrace; }

    @Override
    public String toString() {
        return "ExceptionEvent{" +
                "correlationId='" + correlationId + '\'' +
                ", actor='" + actor + '\'' +
                ", exceptionClass='" + exceptionClass + '\'' +
                ", message='" + message + '\'' +
                ", path='" + path + '\'' +
                ", httpMethod='" + httpMethod + '\'' +
                ", status=" + status +
                ", timestamp=" + timestamp +
                ", stackTrace=" + stackTrace +
                '}';
    }
}
