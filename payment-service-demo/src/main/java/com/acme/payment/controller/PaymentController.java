package com.acme.payment.controller;

import com.acme.payment.dto.TransferRequest;
import com.acme.payment.dto.TransferResult;
import com.acme.payment.entity.Account;
import com.acme.payment.service.PaymentService;
import com.roconmachine.governance.audit.annotation.Auditable;
import com.roconmachine.governance.response.model.AppResponse;
import com.roconmachine.governance.response.model.AppResponseFactory;
import com.platform.security.auth.model.AuthenticatedPrincipal;
import com.platform.security.auth.model.CurrentUser;
import com.platform.security.crypto.annotation.EncryptedAPI;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final AppResponseFactory appResponseFactory;

    public PaymentController(PaymentService paymentService, AppResponseFactory appResponseFactory) {
        this.paymentService = paymentService;
        this.appResponseFactory = appResponseFactory;
    }

    /** Who does security-auth think is calling, right now? Demonstrates CurrentUser. */
    @GetMapping("/whoami")
    public AppResponse<AuthenticatedPrincipal> whoAmI() {
        AuthenticatedPrincipal principal = CurrentUser.get()
                .orElseThrow(
                        () ->  new AccessDeniedException("User is not logged in")
                );
        return appResponseFactory.success(principal, "Authenticated as " + principal.getSubject());
    }

    @GetMapping("/accounts/{accountNumber}")
    public AppResponse<Account> getAccount(@PathVariable String accountNumber) {
        Account account = paymentService.getAccount(accountNumber);
        return appResponseFactory.success(account, "Account retrieved");
    }

    @PostMapping("/transfer")
    public AppResponse<TransferResult> transfer(@RequestBody TransferRequest request) {
        TransferResult result = paymentService.transfer(request);
        return appResponseFactory.success(result, "Transfer completed");
    }

    @PostMapping("/notify/{userPrincipalName}")
    public AppResponse<String> notify(@PathVariable String userPrincipalName) {
        String result = paymentService.notifyViaExchange(userPrincipalName);
        return appResponseFactory.success(result, "Notification dispatched");
    }




    @EncryptedAPI
    @PostMapping("/secure-echo")
    @Auditable(captureArgs = true)
    public AppResponse<Map<String, Object>> secureEcho(@RequestBody Map<String, Object> payload) {
        return appResponseFactory.success(payload, "successfull");
    }
}
