# security-auth

Validates inbound JWTs (signature, expiry, issuer, audience) and populates
Spring Security's context with a typed principal - turning "every request
must present a valid token from our identity provider" into a Maven
dependency instead of each service hand-rolling JWT parsing.

## 1. Add the dependency

```xml
<dependency>
    <groupId>com.platform.security</groupId>
    <artifactId>security-auth</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

`governance-core` comes along transitively - see section 5 for why that
matters even if you don't touch it directly.

## 2. Configure

```yaml
security:
  auth:
    hmac-secret: "BASE64_ENCODED_SECRET"   # shared with whatever issues your tokens
    issuer: payment-service
    audience: internal-platform             # optional - omit to skip audience checks
    roles-claim: roles                      # claim name holding the caller's roles
    reject-invalid-token: true              # 401 immediately if a token is present but invalid
```

Generate a secret for local dev/test:

```bash
openssl rand -base64 32
```

**HMAC (`hmac-secret`) means whatever issues your tokens shares this exact
secret.** That's fine for a single internal identity-issuing service, but if
tokens come from a separate identity provider (Okta, Auth0, Keycloak, your
own auth server), you almost certainly want RSA/EC verification or a JWKS
endpoint instead - swap in your own `TokenValidator` bean for that (see
section 4); `JwtTokenValidator` here is the HMAC-only default.

## 3. Read the authenticated caller in your code

```java
@RestController
public class PaymentController {

    @PostMapping("/transfer")
    public TransferResult transfer(@RequestBody TransferRequest request) {
        AuthenticatedPrincipal caller = CurrentUser.get()
                .orElseThrow(() -> new AccessDeniedException("no authenticated caller"));

        // caller.getSubject() -> the user/service id from the token's `sub` claim
        // caller.getRoles()   -> Set<String>, e.g. {"PAYMENT_ADMIN"}
        ...
    }
}
```

Roles are also available as standard Spring Security authorities
(`ROLE_<role>`), so `@PreAuthorize("hasRole('PAYMENT_ADMIN')")` and
`governance-rbac`'s own checks both work against the same authenticated
context without needing `CurrentUser` at all.

## 4. Swapping in a different identity provider

```java
@Bean
public TokenValidator oktaTokenValidator(OktaJwtVerifier verifier) {
    return token -> {
        var jwt = verifier.decode(token); // your integration, JWKS-based, etc.
        return new AuthenticatedPrincipal(jwt.getSubject(), jwt.getRoles(), jwt.getClaims());
    };
}
```

Registering your own `TokenValidator` bean replaces `JwtTokenValidator`
entirely (`@ConditionalOnMissingBean`) - `JwtAuthenticationFilter` doesn't
change; it only ever depends on the `TokenValidator` interface.

## 5. What a missing vs. invalid token does

| Situation | Behavior |
|---|---|
| No `Authorization` header at all | Request proceeds unauthenticated - public endpoints are unaffected |
| Header present, token invalid/expired/bad signature | `401` immediately if `reject-invalid-token: true` (default); otherwise proceeds unauthenticated and lets downstream authorization decide |

Either way, the filter never echoes *why* a token failed back in the
response body - see `TokenValidationException`'s Javadoc for why: telling a
caller "expired" vs. "bad signature" vs. "wrong audience" is free
reconnaissance for an attacker probing your auth.

## 6. Integration with the rest of the platform

Once a token validates, this module writes the resolved subject into
`governance-core`'s actor MDC key - the same key `governance-audit` and
`governance-http-logging` already read. That means:

- Add `security-auth` to a service that already has `governance-audit` →
  audit events now show the *real* authenticated caller instead of
  `actor=system` or whatever a gateway header happened to say.
- `governance-audit`/`governance-http-logging` need **no dependency on this
  module** to benefit - they just read the MDC key, whoever populates it.

## 7. Governance visibility

```
GET /actuator/securityAuth
```

```json
{
  "auth": {
    "enabled": true,
    "issuer": "payment-service",
    "audience": "internal-platform",
    "rolesClaim": "roles",
    "headerName": "Authorization",
    "clockSkewSeconds": 30,
    "rejectInvalidToken": true,
    "hmacSecretConfigured": true
  },
  "module": "security-auth:0.1.0-SNAPSHOT"
}
```

`hmacSecretConfigured` is a boolean - the secret itself is never exposed,
not even partially.

## Build

```
mvn clean test
```

Tests cover the properties that actually matter for an auth module: valid
tokens resolve correctly, expired tokens are rejected, tokens signed with
the wrong key are rejected, wrong-issuer tokens are rejected, and a missing
roles claim degrades to an empty role set rather than failing.
