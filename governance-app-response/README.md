# governance-app-response

Standardizes application-level response/exception codes across
microservices as **`ServiceID-Type-EventCode`** (e.g. `PAY-B-0001`), plus a
universal `AppResponse<T>` envelope used for *both* success and error
responses - a client integrating against any service on this platform
parses exactly one response shape regardless of outcome.

## ⚠️ Read this before adopting: overlap with `governance-exception-handling`

This platform already has `governance-exception-handling`, built earlier,
which solves the same underlying problem - a consistent error response
shape - with a **different, incompatible** opinion: an `ErrorResponse`
envelope and free-form `errorCode` strings, no `ServiceID-Type-EventCode`
taxonomy.

**Do not depend on both modules in the same service.** Both register a
`@RestControllerAdvice` bean with an `@ExceptionHandler(Exception.class)`
method. Having two advice beans both claim the same exception type risks
Spring's *"Ambiguous `@ExceptionHandler` method mapped for class
Exception"* failure at runtime, and even where it doesn't hard-fail, you'd
get two different, unpredictable response shapes for the same
service depending on which advice bean happens to win.

| | `governance-exception-handling` | `governance-app-response` (this module) |
|---|---|---|
| Response envelope | `ErrorResponse` (error-only; no success shape) | `AppResponse<T>` (success AND error, one shape) |
| Error code format | Free-form `errorCode` string, e.g. `"PAYMENT_NOT_FOUND"` | Structured `ServiceID-Type-EventCode`, e.g. `"PAY-B-0001"` |
| Async logging | Yes - dedicated bounded executor with MDC propagation | No - synchronous SLF4J logging only |
| `AccessDeniedException` (security-rbac / Spring Security) | Mapped to 403, isolated handler class | Mapped to 403 (`ServiceID-B-9403`), isolated handler class - same pattern |
| Best fit | Services that want a lightweight, free-text error code and don't need a formal per-service code registry | Multi-service platforms that want parseable, greppable, per-service-namespaced codes (e.g. for a support runbook keyed by exact code) |

**Recommendation as architect:** pick ONE as the platform standard and
apply it everywhere. If your organization wants a formal, auditable code
registry (a spreadsheet/wiki mapping every `PAY-B-0001`-style code to a
runbook entry), this module's structured format fits that need better. If
you only need a consistent shape without a formal per-service numbering
scheme, the simpler `governance-exception-handling` may be enough. Don't
run both to "get both feature sets" - consolidate.

## 1. Add the dependency

```xml
<dependency>
    <groupId>com.platform.governance</groupId>
    <artifactId>governance-app-response</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## 2. Configure - `application.yml`

```yaml
application:
  response:
    enabled: true
    service-id: PAY              # REQUIRED - exactly 3 alphanumeric chars, e.g. "PAY", "ORD", "101"
    success-event-code: "0000"   # used for the "I" (informational/success) appCode, e.g. PAY-I-0000
    default-system-event-code: "9999"   # used when a non-BaseAppException is caught
```

**Startup validation:** if `service-id` is missing, blank, or not exactly 3
alphanumeric characters, the application **fails to start** with a clear
`InvalidServiceIdException` message - this is deliberate fail-fast behavior,
not a runtime surprise on the first request.

## 3. Define your service's error codes

```java
package com.acme.payment.error;

import com.platform.governance.response.exception.ErrorCode;
import com.platform.governance.response.exception.ExceptionType;
import org.springframework.http.HttpStatus;

public enum PaymentErrorCode implements ErrorCode {

    INSUFFICIENT_FUNDS("0001", ExceptionType.BUSINESS, "Insufficient funds for this transfer", HttpStatus.UNPROCESSABLE_ENTITY),
    ACCOUNT_NOT_FOUND ("0002", ExceptionType.BUSINESS, "Account not found",                     HttpStatus.NOT_FOUND),
    ACCOUNT_FROZEN    ("0003", ExceptionType.BUSINESS, "Account is frozen",                      HttpStatus.CONFLICT),
    DOWNSTREAM_BANK_UNAVAILABLE("1001", ExceptionType.SYSTEM, "Downstream banking system unavailable", HttpStatus.SERVICE_UNAVAILABLE);

    private final String eventCode;
    private final ExceptionType type;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    PaymentErrorCode(String eventCode, ExceptionType type, String defaultMessage, HttpStatus httpStatus) {
        this.eventCode = eventCode;
        this.type = type;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    @Override public String eventCode() { return eventCode; }
    @Override public ExceptionType type() { return type; }
    @Override public String defaultMessage() { return defaultMessage; }
    @Override public HttpStatus httpStatus() { return httpStatus; }
}
```

Note the numbering convention worth adopting platform-wide: `0001-0999`
for business codes, `1001+` for system/infrastructure codes, kept in one
enum per service so every code your service can produce is greppable in one
file.

## 4. Throw from your service code

```java
import com.platform.governance.response.exception.BusinessException;
import com.platform.governance.response.exception.SystemException;
import static com.acme.payment.error.PaymentErrorCode.*;

@Service
public class PaymentService {

    public Payment get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ACCOUNT_NOT_FOUND, "No account with id " + id));
    }

    public void transfer(TransferRequest request) {
        if (isFrozen(request.getFromAccount())) {
            throw new BusinessException(ACCOUNT_FROZEN); // uses ACCOUNT_FROZEN's own default message
        }
        if (insufficientFunds(request)) {
            throw new BusinessException(INSUFFICIENT_FUNDS);
        }
        try {
            bankGateway.submit(request);
        } catch (BankGatewayException e) {
            throw new SystemException(DOWNSTREAM_BANK_UNAVAILABLE, e); // cause preserved for server-side logs
        }
    }
}
```

Nothing to catch or map manually - `GlobalAppExceptionHandler` (registered
automatically) formats every one of these into the standard `AppResponse`
envelope with the correctly-assembled appCode.

## 5. Returning success responses

```java
import com.platform.governance.response.model.AppResponse;
import com.platform.governance.response.model.AppResponseFactory;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final AppResponseFactory appResponseFactory;

    public PaymentController(PaymentService paymentService, AppResponseFactory appResponseFactory) {
        this.paymentService = paymentService;
        this.appResponseFactory = appResponseFactory;
    }

    @GetMapping("/{id}")
    public AppResponse<Payment> get(@PathVariable String id) {
        Payment payment = paymentService.get(id);
        return appResponseFactory.success(payment, "Payment retrieved");
    }
}
```

Response body:

```json
{
  "status": "SUCCESS",
  "httpCode": 200,
  "appCode": "PAY-I-0000",
  "message": "Payment retrieved",
  "data": { "id": "abc123", "amount": 250.00 },
  "timestamp": "2026-07-23T10:15:30Z",
  "traceId": "6521ca85-2699-4665-9b5f-4a924ae489c0"
}
```

And for the `INSUFFICIENT_FUNDS` business exception thrown above:

```json
{
  "status": "ERROR",
  "httpCode": 422,
  "appCode": "PAY-B-0001",
  "message": "Insufficient funds for this transfer",
  "data": null,
  "timestamp": "2026-07-23T10:16:02Z",
  "traceId": "6521ca85-2699-4665-9b5f-4a924ae489c0"
}
```

## 6. What forces Type to `S`, and why

`SystemException` is always `Type=S` - there is no constructor path that
lets it format as `B`. Any exception that ISN'T a `BusinessException` or
`SystemException` at all (a stray `NullPointerException`, a database driver
exception, anything unexpected) is caught by the generic fallback handler,
which:

- Always forces `Type=S` and HTTP 500, regardless of what the underlying
  exception is - an unexpected fault is never reported to a caller as a
  4xx business error.
- Always uses `defaultSystemEventCode` (default `9999`) and a fixed generic
  message (`"An unexpected error occurred"`) - never the raw exception's
  message, since that can easily contain internal details (table names,
  field names). The full exception, with stack trace, is still logged
  server-side.

## 7. Trace ID resolution

`traceId` on every `AppResponse` comes from, in order of preference:

1. **Micrometer Tracing's active span**, if `micrometer-tracing` is on the
   classpath and a span is active - correct across an entire distributed
   trace, not just this one service.
2. **governance-core's correlation-id MDC key** otherwise - the same value
   `governance-audit`/`governance-http-logging` already populate/read, so
   this integrates for free if `governance-core`'s `CorrelationIdFilter` is
   active (it always is, since `governance-core` is a required dependency
   of this module).

No configuration needed either way - the right provider is chosen
automatically at startup based on what's on your classpath.

## 8. Swapping pieces

Every bean is `@ConditionalOnMissingBean`:

```java
@Bean
public TraceIdProvider traceIdProvider(MyCustomTracingBridge bridge) {
    return bridge::currentTraceId;
}
```

## 9. Governance visibility

```
GET /actuator/appResponse
```

```json
{
  "appResponse": {
    "enabled": true,
    "serviceId": "PAY",
    "successEventCode": "0000",
    "defaultSystemEventCode": "9999",
    "exampleSuccessAppCode": "PAY-I-0000",
    "exampleBusinessAppCode": "PAY-B-0001",
    "exampleSystemAppCode": "PAY-S-9999"
  },
  "module": "governance-app-response:0.1.0-SNAPSHOT"
}
```

Nothing sensitive in this module at all - safe to leave enabled everywhere.

## Build

```
mvn clean test
```

Tests cover: service-id validation (valid 3-char alphanumeric ids, and
every rejection case - missing, blank, wrong length, special characters),
the exception hierarchy's forced Type per subclass and event-code shape
validation, the handler's exact appCode assembly for all three paths
(business/system/unhandled), that an unhandled exception's real message
never reaches the response body, and trace-id resolution both with and
without Micrometer Tracing present (active-span preference, MDC fallback).
