package com.platform.governance.audit.publisher;

import com.platform.governance.audit.model.AuditEvent;

public class NoopAuditEventPublisher implements AuditEventPublisher {
    @Override
    public void publish(AuditEvent event) {
        // intentionally discarded - governance.audit.sink=NOOP (dev/test profiles only)
    }
}
