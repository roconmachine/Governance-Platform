# governance-exception-handling

> **⚠️ Overlaps with `governance-app-response`.** Both modules register a
> global `@RestControllerAdvice` solving the same problem (a consistent
> error response shape) with different, incompatible envelopes - this
> module's `ErrorResponse` + free-form `errorCode` strings vs. that
> module's `AppResponse<T>` + structured `ServiceID-Type-EventCode` codes.
> **Do not depend on both in the same service** - two advice beans both
> claiming `Exception.class` risks Spring's "Ambiguous `@ExceptionHandler`
> method mapped" failure at runtime. See `governance-app-response`'s README
> for a full side-by-side comparison and a recommendation on which to
> standardize on.

One `@RestControllerAdvice` every service inherits: a consistent error
envelope for business exceptions, Bean Validation failures, malformed
requests, and anything unhandled - plus asynchronous exception logging so a
slow log sink never adds latency to the error response itself.

## 1. Add the dependency

```xml
<dependency>
    <groupId>com.platform.governance</groupId>
    <artifactId>governance-exception-handling</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

That's it - no `@ExceptionHandler` methods to write in your own controllers.
`GovernanceExceptionHandlingAutoConfiguration` registers the handler
automatically.

## 2. Throw domain exceptions from your service code

```java
import com.platform.governance.exception.exception.NotFoundException;
import com.platform.governance.exception.exception.ConflictException;
import com.platform.governance.exception.exception.BusinessRuleViolationException;

@Service
public class PaymentService {

    public Payment get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("PAYMENT_NOT_FOUND", "No payment with id " + id));
    }

    public void transfer(TransferRequest request) {
        if (accountFrozen(request.getFromAccount())) {
            throw new ConflictException("ACCOUNT_FROZEN", "Source account is frozen");
        }
        if (insufficientFunds(request)) {
            throw new BusinessRuleViolationException("INSUFFICIENT_FUNDS", "Insufficient funds for transfer");
        }
        // ...
    }
}
```

Every one of these - and Bean Validation failures, malformed JSON, missing
request parameters, 404s from unmatched routes, and any other unhandled
exception - comes back through the exact same response shape:

```json
{
  "timestamp": "2026-07-22T10:15:30Z",
  "correlationId": "6521ca85-2699-4665-9b5f-4a924ae489c0",
  "status": 404,
  "errorCode": "PAYMENT_NOT_FOUND",
  "message": "No payment with id abc123",
  "path": "/payments/abc123",
  "validationErrors": []
}
```

A validation failure additionally populates `validationErrors`:

```json
{
  "status": 400,
  "errorCode": "VALIDATION_FAILED",
  "message": "Request failed validation",
  "validationErrors": [
    { "field": "amount", "message": "must be positive" }
  ]
}
```

## 3. What's handled out of the box

| Exception | Status | Error code |
|---|---|---|
| `BusinessException` subclasses (`NotFoundException`, `ConflictException`, `BusinessRuleViolationException`, or your own) | whatever the exception declares | whatever the exception declares |
| `MethodArgumentNotValidException` / `ConstraintViolationException` (Bean Validation) | 400 | `VALIDATION_FAILED` |
| `HttpMessageNotReadableException` (malformed JSON body) | 400 | `MALFORMED_REQUEST` |
| `MissingServletRequestParameterException` | 400 | `MISSING_PARAMETER` |
| `NoHandlerFoundException` | 404 | `NO_HANDLER_FOUND` |
| `AccessDeniedException` (Spring Security / `security-rbac`) - only if Spring Security is on the classpath | 403 | `ACCESS_DENIED` |
| Anything else | 500 | `INTERNAL_ERROR` (configurable) |

The last row is the important safety property: an unexpected
`NullPointerException` or `SQLException` never has its raw message echoed
to the caller - "An unexpected error occurred" is returned regardless of
what the underlying exception actually says, since that message can easily
contain internal details (table names, field names, library internals).
The **full** exception detail still reaches the async log - only the
client-facing message is deliberately generic.

## 4. Custom business exceptions

Extend `BusinessException` directly for anything the three built-in
subclasses don't cover:

```java
public class RateLimitExceededException extends BusinessException {
    public RateLimitExceededException(String message) {
        super("RATE_LIMIT_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
```

It's picked up by the same handler automatically - nothing to register.

## 5. Why the logging is asynchronous, and what that actually means

Every exception - handled or not - is recorded via `AsyncExceptionLogger`,
which hands the actual `publish()` call off to a bounded thread pool. The
request thread builds the `ErrorResponse` and returns immediately; it never
waits on whatever the log sink does (writing to disk, a network call to a
log aggregator, etc.).

**The detail that's easy to get wrong here, and that this module handles
for you:** MDC (correlation id, actor) is `ThreadLocal`. Handing a task to
an executor does not automatically carry that context to the worker thread
- it's not a bug, that's what `ThreadLocal` means. Without doing anything
about it, log lines produced on the worker thread would show a blank
correlation id even though the request clearly had one. This module wires
a `TaskDecorator` (`MdcTaskDecorator`) into the executor specifically to
snapshot and restore MDC context across that boundary - see that class's
Javadoc if you want the full mechanics.

**Under sustained overload**, the bounded queue can fill up. When it does,
the executor's `CallerRunsPolicy` makes the *submitting* thread run the
logging task itself instead of rejecting it - the only failure mode is "this
one log call became synchronous, temporarily," never "this exception was
silently never logged." Losing visibility into exceptions is worse than a
brief latency hit during extreme load.

```yaml
governance:
  exception-handling:
    default-error-code: INTERNAL_ERROR
    include-stack-trace-in-response: false   # true ONLY in local/dev profiles
    async: true                              # false forces synchronous publishing
    async-pool:
      core-pool-size: 2
      max-pool-size: 4
      queue-capacity: 500
      thread-name-prefix: "governance-exception-"
```

## 6. Swapping the log sink

```java
@Bean
public ExceptionEventPublisher sentryExceptionEventPublisher(SentryClient sentryClient) {
    return event -> sentryClient.captureException(event); // your integration
}
```

Replaces `LoggingExceptionEventPublisher` entirely (`@ConditionalOnMissingBean`)
- `AsyncExceptionLogger` and the handlers don't change.

## 7. Governance visibility

```
GET /actuator/governanceExceptionHandling
```

```json
{
  "exceptionHandling": {
    "enabled": true,
    "defaultErrorCode": "INTERNAL_ERROR",
    "includeStackTraceInResponse": false,
    "async": {
      "corePoolSize": 2,
      "maxPoolSize": 4,
      "queueCapacity": 500,
      "threadNamePrefix": "governance-exception-",
      "rejectionPolicy": "CallerRuns (never drops - falls back to synchronous logging under sustained overload)"
    }
  },
  "module": "governance-exception-handling:0.1.0-SNAPSHOT"
}
```

## 8. A note on the AccessDeniedException handler specifically

It's registered in its own class (`SecurityAccessDeniedExceptionHandler`),
separate from the main handler, and only loaded at all when
`spring-security-core` is confirmed on the classpath via
`@ConditionalOnClass`. This module has no obligation on any service to have
Spring Security present - a service with no security dependency at all
still gets every other handler in this module working normally.

## Build

```
mvn clean test
```

Tests cover: each exception type maps to the correct status/error code, the
generic 500 handler never leaks the underlying exception's message to the
client while the async log still gets full detail, `debugDetails` stays
null unless explicitly enabled, logging genuinely happens off the calling
thread, MDC correlation id correctly propagates to the worker thread via
`MdcTaskDecorator`, and - under deliberately induced saturation - every
single submitted event is still published, none silently dropped.
