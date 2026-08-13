package com.platform.governance.archunit.fixtures.good.service;

import com.platform.governance.archunit.fixtures.good.repository.PaymentRepository;
import com.platform.governance.audit.annotation.Auditable;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final PaymentRepository repository;

    public PaymentService(PaymentRepository repository) {
        this.repository = repository;
    }

    @Auditable
    public void transfer(String accountId) {
        repository.save(accountId);
    }

    public String getStatus() {
        // plain accessor - excluded from the @Auditable requirement by name pattern
        return "OK";
    }
}
