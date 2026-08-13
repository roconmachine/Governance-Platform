package com.platform.governance.archunit.fixtures.bad.controller;

import com.platform.governance.archunit.fixtures.bad.repository.PaymentRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final PaymentRepository repository; // violates controllersMustNotDependOnRepositories

    public PaymentController(PaymentRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/transfer")
    public void transfer(String accountId) {
        repository.save(accountId); // bypasses the service layer entirely - no @Auditable possible here
    }
}
