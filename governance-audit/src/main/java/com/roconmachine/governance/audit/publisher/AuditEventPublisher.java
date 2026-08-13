package com.roconmachine.governance.audit.publisher;

import com.roconmachine.governance.audit.model.AuditEvent;

/**
 * Extension point. This module ships a LOG-based default so audit works with
 * zero infra out of the box; a service (or a follow-on governance-audit-kafka
 * module) can register its own bean of this type - e.g. to ship events to a
 * central immutable audit store - and it will be picked up automatically
 * because this module only provides this bean @ConditionalOnMissingBean.
 */
public interface AuditEventPublisher {
    void publish(AuditEvent event);
}
