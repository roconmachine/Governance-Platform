# governance-audit

Turns "every transaction must be audited, PII must never appear in logs"
from policy documents into a Maven dependency. Add the jar (plus its
`governance-core` dependency, pulled in transitively), get the policy - no
service-side wiring required.

## 1. Add the dependency

```xml
<dependency>
    <groupId>com.platform.governance</groupId>
    <artifactId>governance-audit</artifactId>
    <version>{version}</version>
</dependency>
```

`governance-core` (correlation id / actor propagation, `@Sensitive` masking)
comes along transitively - nothing extra to add. `GovernanceAuditAutoConfiguration`
is picked up automatically via Spring Boot's `AutoConfiguration.imports`
mechanism; nothing to `@Import`, nothing to enable.

## 2. Mark governed actions

```java
@Service
public class PaymentService {

    @Auditable(action = "FUNDS_TRANSFER", resource = "PAYMENT")
    public TransferResult transfer(TransferRequest request) {
        // ... business logic
    }
}
```

Every call now produces one `AuditEvent`, whether it succeeds or throws,
carrying the service name (resolved automatically from
`spring.application.name`, or override with `governance.audit.service-name`),
the actor (read from the `X-User-Id` header by default - see
`governance.core.actor-header` - no Spring Security dependency required),
the correlation id propagated from the inbound request, duration, and
masked arguments.

If your gateway, auth proxy, or JWT filter already resolves the caller,
just have it forward that identity in the configured header before the
request reaches an `@Auditable` method. If no actor is available, events
record `actor=system` rather than failing.

### Publishing synchronously vs. asynchronously

```java
@Auditable(action = "FUNDS_TRANSFER", resource = "PAYMENT")           // async by default
public TransferResult transfer(TransferRequest request) { ... }

@Auditable(action = "APPROVE_HIGH_VALUE_TRANSFER", resource = "PAYMENT", async = false)  // opt into blocking
public TransferResult approveHighValueTransfer(TransferRequest request) { ... }  
```

**`async = true` is the default.** The annotated method returns without
waiting on whatever `AuditEventPublisher.publish()` does (log I/O, a
network call to a central audit store) - the publish call is handed off to
a bounded background executor. Set `async = false` for a specific method
only when the audit write must be confirmed BEFORE the method returns.

**A real trade-off, not just a performance knob:** with the default
`async = true`, `governance.audit.fail-on-publish-error` can no longer fail
the caller's own transaction - a rethrow on the async executor thread can't
propagate back to a method that has already returned (or already thrown
its own exception) on the calling thread. That guarantee only exists with
`async = false`. For the rare action where "the audit write failing must
fail the business transaction too" is a real requirement (not just "audit
this action"), set `async = false` explicitly.

Under sustained saturation of the bounded queue, the executor's
`CallerRunsPolicy` falls back to synchronous publishing rather than ever
silently dropping an audit event - the same fail-safe shape used throughout
this platform (see `governance-exception-handling`'s async logging).

```yaml
governance:
  audit:
    service-name: PAY-SERVICE   # optional override; defaults to spring.application.name
    async:
      core-pool-size: 2
      max-pool-size: 4
      queue-capacity: 500
      thread-name-prefix: "governance-audit-"
```

## 3. Mark sensitive fields once (shared across every governance module)

```java
import com.platform.governance.core.annotation.Sensitive;

public class TransferRequest {
    private String fromAccount;

    @Sensitive(strategy = Sensitive.MaskStrategy.PARTIAL)
    private String cardNumber;   // logged as ****1234

    @Sensitive(strategy = Sensitive.MaskStrategy.HASH)
    private String nationalId;   // logged as sha256:ab12cd34ef56
}
```

`@Sensitive` lives in `governance-core`, so the exact same annotation and
masking policy applies whether the field flows through governance-audit
or through `governance-http-logging` - one policy, not two.

## 4. Governance is inspectable at runtime

```
GET /actuator/governance          # shared: correlation id, actor, masking policy (governance-core)
GET /actuator/governance-audit     # audit-specific: sink, failure behavior (this module)
```

```json
{
  "audit": {
    "enabled": true,
    "sink": "LOG",
    "failOnPublishError": false
  },
  "module": "governance-audit:0.1.0-SNAPSHOT",
  "seeAlso": "/actuator/governance (shared correlation-id/actor/masking policy)"
}
```

An auditor or security reviewer can `curl` these on every environment
instead of asking a team to attest to a checklist.

## 5. Central policy, local override

```yaml
governance:
  core:                       # shared across every governance module
    correlation-id-header: X-Correlation-Id
    actor-header: X-User-Id
    mask-sensitive-data: true
  audit:                      # this module only
    enabled: true
    sink: LOG                 # swap to a custom AuditEventPublisher bean for Kafka/DB sinks
    fail-on-publish-error: false
```

A service can override a single piece of behavior (e.g. supply its own
`AuditEventPublisher` to ship events to a central audit store) without
forking the module, because every bean is `@ConditionalOnMissingBean`.


## Build

```
mvn clean verify
```

