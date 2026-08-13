package com.roconmachine.governance.archunit.fixtures.bad.service;

import com.roconmachine.governance.archunit.fixtures.bad.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    @Autowired
    private PaymentRepository repository;

    // Missing @Auditable - this is the violation
    // GovernanceAnnotationRules.publicServiceMethodsShouldBeAuditable should catch it.
    public void transfer(String accountId) {
        System.out.println("transferring " + accountId); // violates noStandardStreamUsage
        repository.save(accountId);
    }
}
