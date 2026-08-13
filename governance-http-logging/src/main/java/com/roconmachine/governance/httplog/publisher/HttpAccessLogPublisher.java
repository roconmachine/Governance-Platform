package com.roconmachine.governance.httplog.publisher;

import com.roconmachine.governance.httplog.model.HttpAccessLogEvent;

/**
 * Extension point, same pattern as governance-audit's
 * AuditEventPublisher: ships with a LOG default, swap in your own bean
 * (e.g. shipping to a central access-log store) without forking the module.
 */
public interface HttpAccessLogPublisher {
    void publish(HttpAccessLogEvent event);
}
