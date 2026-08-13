package com.acme.payment.config;

import com.acme.payment.entity.Account;
import com.acme.payment.repository.AccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DemoDataSeeder implements CommandLineRunner {

    private final AccountRepository accountRepository;

    public DemoDataSeeder(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public void run(String... args) {
        accountRepository.save(new Account("ACC-001", "4111111111111234", new BigDecimal("1000.00")));
        accountRepository.save(new Account("ACC-002", "5500005555555559", new BigDecimal("250.00")));
    }
}
