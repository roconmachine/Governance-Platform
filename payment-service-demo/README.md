# payment-service-demo

A sample microservice exercising **every module** in `governance-platform`,
depended on the way a real external service would - by Maven coordinates,
not as a reactor sibling. This is the integration proof that the modules
actually compose correctly in one application.

> **Reflects the latest platform changes:** Spring Boot bumped to `3.5.16`
> (Java 21) across every module; `security-encryption` renamed to
> `security-crypto` with the new `@EncryptedAPI` whole-payload feature;
> `governance-audit`'s `@Auditable` now defaults to `async = true` (was
> `false`); `governance-http-logging` now captures query string/request
> parameters by default and publishes asynchronously by default;
> `governance-exception-handling` gained a sync/async toggle (still
> defaults to async, its original behavior).

## Prerequisite

Build and install the reactor first, so these artifacts exist in your local
`~/.m2` repository:

```bash
cd governance-platform
mvn clean install
```

Then build this project:

```bash
cd payment-service-demo
mvn clean install
```

## What's wired in, and where to see it

| Module | Where it shows up in this demo |
|---|---|
| `governance-core` | Transitive; `X-Correlation-Id`/`X-User-Id` handling underlies everything below |
| `governance-audit` | `PaymentService`'s `@Auditable` methods - `transfer()` (explicit `async=false`), `notifyViaExchange()`/`encryptForDownstream()` (async, the default) |
| `governance-http-logging` | Active on every request automatically - check the `HTTP_ACCESS` logger output (now includes query string + request params, masked by name) |
| `governance-app-response` | `PaymentController` returns `AppResponse<T>`; `PaymentErrorCode` is this service's error registry |
| `governance-archunit` | `ArchitectureGovernanceTest` - this service's own layering/naming/audit-annotation rules, enforced in `mvn test` |
| `security-crypto` | `Account.cardNumber` (JPA converter), `PaymentService.encryptForDownstream()` (direct API use), `PaymentController.secureEcho()` (NEW: `@EncryptedAPI` whole-payload encryption) |
| `security-auth` | `PaymentController.whoAmI()` uses `CurrentUser`; the JWT filter is active on every request |
| `security-rbac` | `@RequiresPermission("payment:read"/"payment:transfer")` on `PaymentService`'s methods |
| `security-auth-ms-exchange` | `PaymentService.notifyViaExchange()` calls `ExchangeAuthProvider.getAccessToken()` |

## ⚠️ Why `governance-app-response`, not `governance-exception-handling`

Both solve the same problem (a consistent error envelope) and are
documented alternatives, not complements - using both risks Spring's
"Ambiguous `@ExceptionHandler` method mapped" failure, since both register
a `@RestControllerAdvice` claiming `Exception.class`. This demo picked
`governance-app-response` for its structured `ServiceID-Type-EventCode`
codes and unified success/error envelope. If you'd rather see
`governance-exception-handling` wired in instead, swap the dependency in
`pom.xml` and replace `AppResponseFactory`/`AppResponse<T>` usage in
`PaymentController` with that module's equivalents - see its README.

**A real gap this demo surfaced and fixed at the module level:**
`governance-app-response` had no handling for `security-rbac`'s
`AccessDeniedException` - it would have fallen through to the generic
500 handler instead of returning 403. Building this demo is what caught
that; `SecurityAccessDeniedExceptionHandler` was added to
`governance-app-response` to close the gap (isolated via
`@ConditionalOnClass`, same pattern used throughout this platform).

## Demo credentials, stated plainly

Every secret-shaped value in `application.yml` (`security.crypto.keys.default`,
`security.auth.hmac-secret`, `exchange.auth.client-secret`, the fake tenant/client
ids) is a **placeholder for local demo purposes only**. Generate real ones and
inject via environment variables/a secrets manager for anything beyond your
own machine - see each module's own README (`security-crypto`,
`security-auth`, `security-auth-ms-exchange`) for exactly how.

## Running it

```bash
mvn spring-boot:run
```

Two demo accounts are seeded on startup (`DemoDataSeeder`): `ACC-001`
($1000.00) and `ACC-002` ($250.00).

Generate a test JWT matching `application.yml`'s `security.auth` config
(HS256, issuer `payment-service-demo`, a `roles` claim) - any JWT library or
[jwt.io](https://jwt.io) with the demo HMAC secret works for local testing.
Example claims:

```json
{
  "sub": "alice@acme.com",
  "iss": "payment-service-demo",
  "roles": ["PAYMENT_ADMIN"],
  "exp": 9999999999
}
```

```bash
TOKEN="<your generated JWT>"

# security-auth resolves the caller; governance-audit records it as the actor
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/payments/whoami

# security-rbac requires payment:read (PAYMENT_ADMIN and PAYMENT_VIEWER both have it)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/payments/accounts/ACC-001

# security-rbac requires payment:transfer (PAYMENT_ADMIN only, per application.yml)
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"fromAccountNumber":"ACC-001","toAccountNumber":"ACC-002","amount":50.00}' \
     http://localhost:8080/payments/transfer

# a deliberately unknown account - demonstrates governance-app-response's
# BusinessException -> AppResponse<T> mapping (PAY-B-0001, HTTP 404)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/payments/accounts/DOES-NOT-EXIST

# security-auth-ms-exchange - this one needs a REAL Entra tenant/client/secret
# to actually succeed; with the demo placeholders it will throw
# TokenAcquisitionException, caught and mapped to SystemException (PAY-S-1001)
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/payments/notify/alice@acme.com

# security-crypto's @EncryptedAPI - plain request, ENCRYPTED response.
# The response body will be {"data": "<ciphertext>"} - decrypt it with the
# key in application.yml's security.crypto.keys.default to see the echoed
# payload again.
curl -X POST -H "Content-Type: application/json" \
     -d '{"message":"hello"}' http://localhost:8080/payments/secure-echo
```

## Demonstrating `@EncryptedAPI`'s request-side decryption

To see the DECRYPT direction too, encrypt a payload with the same AES-GCM
key and send it with the header. A quick way using this project's own
`AesGcmEncryptionService` from a scratch file or a REPL:

```java
var props = new SecurityCryptoProperties();
props.getKeys().put("default", "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=");
var service = new AesGcmEncryptionService(new EnvKeyProvider(props), props);
String ciphertext = service.encrypt("{\"message\":\"hi from an encrypted caller\"}");
System.out.println(ciphertext);
```

```bash
curl -X POST -H "Content-Type: application/json" -H "X-Encrypted: true" \
     -d "{\"data\":\"<paste the ciphertext here>\"}" \
     http://localhost:8080/payments/secure-echo
```

## Governance visibility - every module's policy in one place

```bash
curl http://localhost:8080/actuator/governance              # correlation id / actor / masking policy
curl http://localhost:8080/actuator/governanceAudit           # audit sink, async pool config
curl http://localhost:8080/actuator/governanceHttpLogging     # HTTP logging policy
curl http://localhost:8080/actuator/appResponse                # ServiceID-Type-EventCode config
curl http://localhost:8080/actuator/securityEncryption         # encryption algorithm/key ids (never key material)
curl http://localhost:8080/actuator/securityAuth                # JWT validation policy (never the secret)
curl http://localhost:8080/actuator/securityRbac                # the full role-permission map
curl http://localhost:8080/actuator/exchangeAuth                # Entra tenant/client id, credential type in use
```

## Testing

```bash
mvn test
```

- `PaymentServiceContextLoadsTest` - the whole stack (every module's
  auto-configuration) wires up in one Spring context without conflict;
  confirms transparent field encryption round-trips correctly; confirms
  `BusinessException` is thrown for a missing account (after authenticating
  as a role with `payment:read`, since `@RequiresPermission` enforces
  *before* the business logic runs - this tripped up the first draft of
  this very test).
- `ArchitectureGovernanceTest` - this service's own layering, naming, and
  "every `@Service` method must be `@Auditable`" rules from
  `governance-archunit`, enforced against `com.acme.payment`'s own code.

`exchange.auth` is disabled specifically for the context-load test
(`@TestPropertySource(properties = "exchange.auth.enabled=false")`) so the
test's success doesn't depend on MSAL4J's internal behavior with
placeholder tenant credentials remaining network-call-free at startup
across every MSAL4J version - a property worth not silently relying on.
