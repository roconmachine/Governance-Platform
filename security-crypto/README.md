# security-crypto

*(renamed from `security-encryption` when the `@EncryptedAPI` whole-payload
feature was added - the module now covers more than just field-level
encryption, so the name changed to match.)*

Turns "card numbers and national IDs must be encrypted at rest" AND
"this endpoint's entire request/response payload must be encrypted in
transit" into a Maven dependency: AES-256-GCM encryption, a pluggable key
provider, a JPA converter for transparent field-level encryption, and an
`@EncryptedAPI` annotation for whole-payload REST encryption.

## 1. Add the dependency

```xml
<dependency>
    <groupId>com.platform.security</groupId>
    <artifactId>security-crypto</artifactId>
    <version>{version}</version>
</dependency>
```

**If you're upgrading from `security-encryption`:** the artifactId changed,
every Java package moved from `com.platform.security.encryption` to
`com.platform.security.crypto` (and the `crypto` sub-package that already
existed became `crypto.engine` to avoid a `crypto.crypto` stutter), and the
configuration prefix changed from `security.encryption.*` to
`security.crypto.*`. Update imports, the dependency coordinate, and your
`application.yml` prefix together.

## 2. Field-level encryption (unchanged behavior, new prefix)

```yaml
security:
  crypto:
    default-key-id: default
    keys:
      default: "BASE64_ENCODED_32_BYTE_KEY_HERE"
```

```java
@Entity
public class Account {
    @Encrypted
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "card_number")
    private String cardNumber;
}
```

See the rest of this section in the original module's design - nothing
about `EncryptionService`, `KeyProvider`, or `EncryptedStringConverter`
changed behaviorally, only their package.

## 3. NEW: `@EncryptedAPI` - whole-payload REST encryption

```java
@RestController
public class PaymentController {

    @EncryptedAPI
    @PostMapping("/transfer")
    public TransferResult transfer(@RequestBody TransferRequest request) {
        // `request` is already DECRYPTED by the time this method runs.
        // The return value is automatically ENCRYPTED before being sent -
        // this method has no idea either transformation is happening.
        ...
    }
}
```

**Response side (always active for an annotated endpoint):** the return
value is serialized to JSON, encrypted, and sent back as
`{"data": "<ciphertext>"}` (field name configurable). The response also
gets an `X-Encrypted: true` header so the client knows to decrypt.

**Request side (opt-in per request, via header):** a caller sends
`{"data": "<ciphertext>"}` as the raw request body AND sets
`X-Encrypted: true`. This filter decrypts it into the real JSON before
`@RequestBody` binding runs. A request WITHOUT that header proceeds
completely unmodified - even to an `@EncryptedAPI` endpoint, so you can
support encrypted and plaintext callers on the same endpoint if needed.

```yaml
security:
  crypto:
    encrypted-api:
      enabled: true
      header-name: X-Encrypted        # the header name - configurable, not hardcoded
      header-true-value: "true"       # the value that means "this body is encrypted"
      payload-field-name: data         # the JSON field carrying ciphertext, both directions
```

### How it works, and the one real trade-off worth knowing

A Servlet `Filter` runs BEFORE Spring MVC has decided which controller
method will handle a request - so to know whether `@EncryptedAPI` even
applies, `EncryptedApiRequestDecryptionFilter` resolves the target handler
itself via `RequestMappingHandlerMapping.getHandler()`, the exact method
the real dispatch calls again moments later. **Handler resolution happens
twice per request** for this reason - an accepted trade-off for making
decryption strictly annotation-driven (not "decrypt anything with this
header, whether the endpoint asked for it or not").

Response-side encryption uses Spring MVC's `ResponseBodyAdvice` - the
supported extension point for exactly this "transform the body right
before serialization" need, not a raw response-wrapping filter.

### Isolation from non-web consumers

This feature depends on `spring-boot-starter-web` (`Filter`,
`ResponseBodyAdvice`, `RequestMappingHandlerMapping`), declared `optional`
in this module's POM. A service using only `EncryptionService`/`KeyProvider`/
the JPA converter (e.g. a batch job with no web layer) never pulls in Spring
MVC, and the `@EncryptedAPI`-specific classes are isolated in their own
`@ConditionalOnClass`-gated configuration - the same isolation lesson
applied throughout this platform.

## 4. Governance visibility

```
GET /actuator/securityCrypto
```

Never exposes key material - only algorithm, configured key ids, and (now)
whether `@EncryptedAPI` is active and its header/field configuration.

## Build

```
mvn clean test
```

Tests cover: AES-GCM round-trip/tamper-detection/wrong-key correctness (as
before), plus `@EncryptedAPI`'s `supports()` gating, response encryption
producing the correct envelope shape, and the request filter's three
decision paths - annotated+header (decrypts), unannotated+header (does
NOT decrypt, since the endpoint never opted in), and annotated+no-header
(does NOT decrypt, since the caller didn't signal an encrypted body).
