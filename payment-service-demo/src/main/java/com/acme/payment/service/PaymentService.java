package com.acme.payment.service;

import com.acme.payment.entity.Account;
import com.acme.payment.error.PaymentErrorCode;
import com.acme.payment.repository.AccountRepository;
import com.roconmachine.governance.audit.annotation.Auditable;
import com.roconmachine.governance.response.exception.BusinessException;
import com.roconmachine.governance.response.exception.SystemException;
import com.platform.security.crypto.engine.EncryptionService;
import com.platform.security.exchange.exception.TokenAcquisitionException;
import com.platform.security.exchange.service.ExchangeAuthProvider;
import com.platform.security.rbac.annotation.RequiresPermission;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.acme.payment.dto.TransferRequest;
import com.acme.payment.dto.TransferResult;

@Service
public class PaymentService {

    private final AccountRepository accountRepository;
    private final EncryptionService encryptionService;
    private final ExchangeAuthProvider exchangeAuthProvider;

    public PaymentService(AccountRepository accountRepository,
                           EncryptionService encryptionService,
                           ExchangeAuthProvider exchangeAuthProvider) {
        this.accountRepository = accountRepository;
        this.encryptionService = encryptionService;
        this.exchangeAuthProvider = exchangeAuthProvider;
    }

    /**
     * Read-only lookup - governance-audit records who looked up which
     * account, security-rbac requires the "payment:read" permission
     * (granted to both PAYMENT_ADMIN and PAYMENT_VIEWER per application.yml).
     */
    @RequiresPermission("payment:read")
    @Auditable(action = "ACCOUNT_LOOKUP", resource = "ACCOUNT")
    public Account getAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.ACCOUNT_NOT_FOUND,
                        "No account with number " + accountNumber));
    }

    /**
     * The main business action - requires "payment:transfer" (PAYMENT_ADMIN
     * only, per application.yml's role-permissions), and is audited
     * SYNCHRONOUSLY via explicit {@code async = false} - governance-audit's
     * default is async, but this is exactly the kind of compliance-critical
     * write that should block until the audit record is confirmed written,
     * so it opts out of the default.
     */
    @RequiresPermission("payment:transfer")
    @Auditable(action = "FUNDS_TRANSFER", resource = "PAYMENT", captureArgs = true, async = false)
    public TransferResult transfer(TransferRequest request) {
        Account from = accountRepository.findByAccountNumber(request.getFromAccountNumber())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.ACCOUNT_NOT_FOUND,
                        "No account with number " + request.getFromAccountNumber()));
        Account to = accountRepository.findByAccountNumber(request.getToAccountNumber())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.ACCOUNT_NOT_FOUND,
                        "No account with number " + request.getToAccountNumber()));

        if (from.isFrozen()) {
            throw new BusinessException(PaymentErrorCode.ACCOUNT_FROZEN);
        }
        if (from.getBalance().compareTo(request.getAmount()) < 0) {
            throw new BusinessException(PaymentErrorCode.INSUFFICIENT_FUNDS);
        }

        from.debit(request.getAmount());
        to.credit(request.getAmount());
        accountRepository.save(from);
        accountRepository.save(to);

        return new TransferResult(UUID.randomUUID().toString(), "COMPLETED", Instant.now());
    }

    /**
     * Demonstrates security-crypto used directly (not just via the JPA
     * converter) - encrypting a value before handing it to some other
     * system that isn't this service's own database. Annotated @Auditable
     * too - who encrypted what for downstream transmission is itself worth
     * a governed record in a fintech context, and this service's own
     * ArchUnit test (see ArchitectureGovernanceTest) enforces that every
     * public @Service method carries the annotation.
     */
    @Auditable(action = "ENCRYPT_FOR_DOWNSTREAM", resource = "ENCRYPTION")
    public String encryptForDownstream(String rawValue) {
        return encryptionService.encrypt(rawValue);
    }

    /**
     * Calls Microsoft Graph app-only, using security-auth-ms-exchange, to
     * pull mailbox activity relevant to a payment notification. Audited
     * ASYNCHRONOUSLY (governance-audit's default - kept explicit here for
     * readability): this is a lower-stakes read against an external system,
     * not a funds-moving action, so the latency win from not blocking on
     * the audit write is worth the (documented, see governance-audit's
     * README) trade-off that fail-on-publish-error can't fail this call if
     * the audit write itself fails.
     */
    @Auditable(action = "NOTIFY_VIA_EXCHANGE", resource = "EXCHANGE_INTEGRATION", async = true)
    public String notifyViaExchange(String userPrincipalName) {
        try {
            String accessToken = exchangeAuthProvider.getAccessToken();
            // In a real service: call Graph here using `accessToken`, e.g.
            // POST https://graph.microsoft.com/v1.0/users/{upn}/sendMail
            return "notified:" + userPrincipalName + ":token-acquired=" + (accessToken != null);
        } catch (TokenAcquisitionException e) {
            throw new SystemException(PaymentErrorCode.DOWNSTREAM_EXCHANGE_UNAVAILABLE, e);
        }
    }
}
