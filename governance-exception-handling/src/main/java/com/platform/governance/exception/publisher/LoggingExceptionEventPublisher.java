package com.platform.governance.exception.publisher;

import com.platform.governance.exception.model.ExceptionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

public class LoggingExceptionEventPublisher implements ExceptionEventPublisher {

    private static final Logger EXCEPTION_LOG = LoggerFactory.getLogger("EXCEPTION");
    private static final org.slf4j.Marker EXCEPTION_MARKER = MarkerFactory.getMarker("GOVERNANCE_EXCEPTION");

    @Override
    public void publish(ExceptionEvent event) {
        if (event.getStatus() >= 500) {
            EXCEPTION_LOG.error(EXCEPTION_MARKER, "{}", event);
        } else {
            // 4xx are still worth a structured record, but shouldn't page anyone -
            // WARN, not ERROR, keeps alerting rules that filter on level sane.
            EXCEPTION_LOG.warn(EXCEPTION_MARKER, "{}", event);
        }
    }
}
