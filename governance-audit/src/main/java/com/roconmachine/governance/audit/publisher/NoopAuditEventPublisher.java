package com.roconmachine.governance.audit.publisher;

import com.roconmachine.governance.audit.model.AuditEvent;

public class NoopAuditEventPublisher implements AuditEventPublisher {
    @Override
    public void publish(AuditEvent event) {
        // intentionally discarded - governance.audit.sink=NOOP (dev/test profiles only)
    }
}
