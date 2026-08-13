package com.roconmachine.governance.audit.publisher;

import com.roconmachine.governance.audit.model.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

/**
 * Zero-infra default sink: writes a structured audit line to a dedicated
 * logger ("AUDIT") with a marker, so downstream log shippers (ELK, Splunk,
 * CloudWatch) can route/index it separately from application logs without
 * any extra service-side wiring.
 */
public class LoggingAuditEventPublisher implements AuditEventPublisher {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT");
    private static final org.slf4j.Marker AUDIT_MARKER = MarkerFactory.getMarker("GOVERNANCE_AUDIT");

    @Override
    public void publish(AuditEvent event) {
        AUDIT_LOG.info(AUDIT_MARKER, "{}", event);
    }
}
