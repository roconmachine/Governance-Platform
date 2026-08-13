package com.platform.security.rbac.fixtures;

import com.platform.security.rbac.annotation.RequiresPermission;
import com.platform.security.rbac.annotation.RequiresRole;

public class SampleService {

    @RequiresRole({"PAYMENT_ADMIN", "PAYMENT_SUPERVISOR"})
    public String adminOnlyAction() {
        return "done";
    }

    @RequiresRole(value = {"PAYMENT_ADMIN", "PAYMENT_SUPERVISOR"}, requireAll = true)
    public String requiresBothRoles() {
        return "done";
    }

    @RequiresPermission("payment:approve")
    public String approveTransfer() {
        return "approved";
    }
}
