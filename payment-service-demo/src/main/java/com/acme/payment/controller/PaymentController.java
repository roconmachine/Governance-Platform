package com.acme.payment.controller;


import com.roconmachine.governance.audit.annotation.Auditable;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class PaymentController {

    @Auditable(captureArgs = true)
    @GetMapping("/hello")
    public ResponseEntity<String> hello(@RequestParam("name") String name){
        return ResponseEntity.ok("hi ".concat(name));
    }
}
