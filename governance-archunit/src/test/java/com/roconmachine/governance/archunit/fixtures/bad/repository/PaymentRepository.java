package com.roconmachine.governance.archunit.fixtures.bad.repository;

import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {
    public void save(String id) {
        // fixture only
    }
}
