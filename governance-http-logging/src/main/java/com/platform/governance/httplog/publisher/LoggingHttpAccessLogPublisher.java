package com.platform.governance.httplog.publisher;

import com.platform.governance.httplog.model.HttpAccessLogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

/**
 * Writes to a dedicated "HTTP_ACCESS" logger with its own marker, separate
 * from the "AUDIT" logger governance-audit uses - so log shippers
 * can route the (much higher-volume) access log differently from the
 * (low-volume, compliance-critical) audit trail.
 */
public class LoggingHttpAccessLogPublisher implements HttpAccessLogPublisher {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("HTTP_ACCESS");
    private static final org.slf4j.Marker ACCESS_MARKER = MarkerFactory.getMarker("GOVERNANCE_HTTP_ACCESS");

    @Override
    public void publish(HttpAccessLogEvent event) {
        ACCESS_LOG.info(ACCESS_MARKER, "{}", event);
    }
}
