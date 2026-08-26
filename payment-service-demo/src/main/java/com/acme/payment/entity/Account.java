package com.acme.payment.entity;

import com.roconmachine.security.crypto.annotation.Encrypted;
import com.roconmachine.security.crypto.config.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String accountNumber;

    /**
     * Transparently encrypted at the JPA persistence boundary - read/write
     * this field as plain Java everywhere else in the codebase; the H2
     * column stores ciphertext only. See security-crypto's README for
     * why both annotations are needed together.
     */
    @Encrypted
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "card_number")
    private String cardNumber;

    private java.math.BigDecimal balance;

    private boolean frozen;

    protected Account() {
        // JPA
    }

    public Account(String accountNumber, String cardNumber, java.math.BigDecimal balance) {
        this.accountNumber = accountNumber;
        this.cardNumber = cardNumber;
        this.balance = balance;
        this.frozen = false;
    }

    public Long getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public String getCardNumber() { return cardNumber; }
    public java.math.BigDecimal getBalance() { return balance; }
    public boolean isFrozen() { return frozen; }

    public void debit(java.math.BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }

    public void credit(java.math.BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}
