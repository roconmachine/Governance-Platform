package com.roconmachine.governance.exception.publisher;

import com.roconmachine.governance.exception.model.ExceptionEvent;

/**
 * Extension point, same pattern as governance-audit's AuditEventPublisher
 * and governance-http-logging's HttpAccessLogPublisher: ships with a LOG
 * default, swap in your own bean (e.g. shipping to a central error-tracking
 * system like Sentry) without forking the module.
 */
public interface ExceptionEventPublisher {
    void publish(ExceptionEvent event);
}
