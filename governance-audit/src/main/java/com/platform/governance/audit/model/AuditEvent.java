package com.platform.governance.audit.model;

import java.time.Instant;

/**
 * The governed, immutable record of a business action. Every field here
 * exists because a compliance requirement said "we must be able to answer
 * who did what, when, with what outcome" - captured once in code, applied
 * to every @Auditable method fleet-wide.
 */
public final class AuditEvent {

    private final String serviceName;
    private final String correlationId;
    private final String action;
    private final String resource;
    private final String actor;
    private final String outcome;      // SUCCESS | FAILURE
    private final String detail;       // masked args/result or error summary
    private final long durationMillis;
    private final Instant timestamp;

    public AuditEvent(String serviceName, String correlationId, String action, String resource, String actor,
                       String outcome, String detail, long durationMillis, Instant timestamp) {
        this.serviceName = serviceName;
        this.correlationId = correlationId;
        this.action = action;
        this.resource = resource;
        this.actor = actor;
        this.outcome = outcome;
        this.detail = detail;
        this.durationMillis = durationMillis;
        this.timestamp = timestamp;
    }

    public String getServiceName() { return serviceName; }
    public String getCorrelationId() { return correlationId; }
    public String getAction() { return action; }
    public String getResource() { return resource; }
    public String getActor() { return actor; }
    public String getOutcome() { return outcome; }
    public String getDetail() { return detail; }
    public long getDurationMillis() { return durationMillis; }
    public Instant getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "AuditEvent{" +
                "serviceName='" + serviceName + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", action='" + action + '\'' +
                ", resource='" + resource + '\'' +
                ", actor='" + actor + '\'' +
                ", outcome='" + outcome + '\'' +
                ", detail='" + detail + '\'' +
                ", durationMillis=" + durationMillis +
                ", timestamp=" + timestamp +
                '}';
    }
}
