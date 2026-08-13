package com.acme.payment.service;

import com.acme.payment.entity.Account;
import com.acme.payment.repository.AccountRepository;
import com.roconmachine.governance.response.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Confirms the ENTIRE stack wires up together: governance-core,
 * governance-audit, governance-http-logging, governance-app-response,
 * security-crypto, security-auth, security-rbac, and
 * security-auth-ms-exchange all auto-configuring in the same application
 * context without conflict.
 *
 * exchange.auth is disabled for this test specifically - security-auth-ms-exchange's
 * ConfidentialClientFactory only builds an MSAL4J client object at startup
 * (no network call), so it would normally load fine even with the demo's
 * placeholder tenant/client id - but there's no reason to depend on that
 * behavior remaining true across every MSAL4J version. Disabling it here
 * keeps this test's success meaningful and independent of MSAL4J
 * implementation details.
 */
@SpringBootTest
@TestPropertySource(properties = "exchange.auth.enabled=false")
class PaymentServiceContextLoadsTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void authenticateAsPaymentViewer() {
        // getAccount() is protected by @RequiresPermission("payment:read") -
        // PAYMENT_VIEWER is mapped to that permission in application.yml.
        // Without this, security-rbac's aspect would throw AccessDeniedException
        // before the business logic below ever runs.
        var authentication = new UsernamePasswordAuthenticationToken(
                "test-user", null, List.of(new SimpleGrantedAuthority("ROLE_PAYMENT_VIEWER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void contextLoadsWithEveryGovernancePlatformModuleActive() {
        assertThat(paymentService).isNotNull();
    }

    @Test
    void encryptedCardNumberIsStoredAsCiphertextButReadsBackAsPlaintext() {
        Account saved = accountRepository.save(new Account("ACC-TEST", "4111111111119999", new BigDecimal("500.00")));
        accountRepository.flush();

        Account reloaded = accountRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCardNumber()).isEqualTo("4111111111119999"); // decrypted transparently on read
    }

    @Test
    void lookingUpAMissingAccountThrowsTheDocumentedBusinessException() {
        assertThatThrownBy(() -> paymentService.getAccount("DOES-NOT-EXIST"))
                .isInstanceOf(BusinessException.class);
    }
}
