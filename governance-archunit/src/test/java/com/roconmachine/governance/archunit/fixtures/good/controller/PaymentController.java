package com.roconmachine.governance.archunit.fixtures.good.controller;

import com.roconmachine.governance.archunit.fixtures.good.service.PaymentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/transfer")
    public void transfer(String accountId) {
        paymentService.transfer(accountId);
    }
}
