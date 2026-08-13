package com.platform.governance.audit.aspect.fixtures;

import com.platform.governance.audit.annotation.Auditable;

public class SampleAuditedService {

    @Auditable(action = "SYNC_ACTION", resource = "TEST", async = false)
    public String syncAction() {
        return "sync-result";
    }

    @Auditable(action = "ASYNC_ACTION", resource = "TEST", async = true)
    public String asyncAction() {
        return "async-result";
    }

    // async defaults to true now - this fixture exercises that default explicitly (no attribute set)
    @Auditable(action = "DEFAULT_ACTION", resource = "TEST")
    public String defaultAction() {
        return "default-result";
    }

    @Auditable(action = "FAILING_ACTION", resource = "TEST", async = false)
    public void failingAction() {
        throw new IllegalStateException("boom");
    }
}
