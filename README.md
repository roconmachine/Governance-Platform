# governance-platform

Governance-as-code and security-as-code module suite for the fintech
middleware platform. Each module is independently publishable; this root
POM is only a reactor convenience for local development.

```
governance-platform/
├── governance-core/                 shared: correlation id, actor propagation, @Sensitive masking
├── governance-audit/                @Auditable business-action audit trail
├── governance-http-logging/         HTTP request/response access logging
├── governance-archunit/             reusable ArchUnit rule factories (test-scope library)
├── governance-exception-handling/   global @RestControllerAdvice + async exception logging
├── governance-app-response/         ⚠️ OVERLAPS with the module above - see its README before adopting either
├── security-crypto/                 AES-GCM encryption + pluggable KeyProvider + JPA converter + @EncryptedAPI
├── security-auth/                   JWT validation, populates Spring Security's context
├── security-rbac/                   @RequiresRole/@RequiresPermission enforcement
└── security-auth-ms-exchange/       Entra ID / Exchange Online OAuth2 (MSAL4J: Client Credentials + OBO)
```

> **Recent platform-wide changes:** Spring Boot bumped to `3.5.16` (Java 21)
> across every module. `security-encryption` renamed to `security-crypto`
> and gained `@EncryptedAPI` (whole-payload REST request/response
> encryption - see that module's README). `governance-audit`'s
> `@Auditable.async()` now defaults to `true` (was `false`) - existing code
> relying on the old synchronous default must now set `async = false`
> explicitly. `governance-http-logging` now captures query string/request
> parameters by default (masked by name) and publishes asynchronously by
> default. `governance-exception-handling` gained a sync/async toggle
> (`governance.exception-handling.async`, still defaults to `true`, its
> original only behavior).

### Naming: plain domain names, no "-starter" suffix

Earlier drafts of this suite used a `-starter`/`-autoconfigure` split
mirroring Spring Boot's own convention (where `spring-boot-starter-web` is a
zero-code dependency bundle and the real logic lives in a separate
`spring-boot-autoconfigure` jar). That split exists in Spring Boot to let
unknown third parties swap implementations under one stable public
dependency name. It doesn't buy anything here: this is an internal platform
with one implementation per concern, consumed by teams we know - splitting
each concern into two artifacts just to preserve a naming pattern from a
different problem was ceremony, not value. So every module is just its
domain name (`governance-audit`, `security-auth`, ...) - depend on it
directly, there's nothing else to add underneath it. This applies across
both the `governance-*` and `security-*` families; keep new modules in
either family named the same plain way.

`governance-archunit` is the one exception worth calling out: it's a
test-scope library, not auto-configured at runtime like the rest - see its
README for the (small, unavoidable) per-service wiring it requires.

## Two families, one convention

- **`governance-*`** - observability/compliance concerns: what happened,
  who did it, is it traceable. Nothing here can reject a request; at most
  it records something.
- **`security-*`** - concerns that can actually deny a request: encryption,
  authentication, authorization. `security-auth` and `security-rbac` depend
  on `spring-security-core` directly and non-optionally, unlike the
  `governance-*` modules - see "Design pattern" point 3 below for why that's
  the correct call here, not an inconsistency.

They compose without any module in one family needing to know about a
specific module in the other:

```
security-auth   → validates the JWT, populates Spring Security's context,
                    AND writes the resolved identity into governance-core's
                    actor MDC key
governance-audit / governance-http-logging
                → read that same MDC key, with zero dependency on security-auth
security-rbac   → enforces @RequiresRole/@RequiresPermission against
                    whatever Authentication is in the context - doesn't care
                    whether security-auth or something else put it there
```

Add `security-auth` to a service that already has `governance-audit` and
audit events immediately show the real authenticated caller instead of
`actor=system` - no code change in either module required.

## Build everything

```
mvn clean install
```

Maven's reactor resolves the dependency order automatically - you don't
need to build modules individually or in any particular sequence.

## Which module do I read first?

- New to the suite → skim `governance-core` first - it's small
  (`Sensitive`, `SensitiveDataMasker`, `CorrelationIdFilter`,
  `GovernanceCoreProperties`).
- Adding audit trails to a service → `governance-audit/README.md`
- Adding HTTP access logging to a service → `governance-http-logging/README.md`
- Enforcing architecture/layering rules in a service's own CI →
  `governance-archunit/README.md` - note this one requires a small amount
  of per-service wiring; see its README
- A consistent error response shape + async exception logging →
  `governance-exception-handling/README.md`
- A consistent error response shape with a formal `ServiceID-Type-EventCode`
  taxonomy → `governance-app-response/README.md` - **read the overlap
  warning in both READMEs before picking one; do not use both together**
- Encrypting sensitive fields at rest → `security-crypto/README.md`
- Validating JWTs / populating the security context → `security-auth/README.md`
- Enforcing role/permission checks → `security-rbac/README.md`
- Calling Microsoft Exchange Online / Graph as a service or on a user's behalf →
  `security-auth-ms-exchange/README.md`

## Design pattern used throughout

Worth internalizing once rather than re-learned per module:

1. **`@AutoConfiguration` + `@ConditionalOnProperty(..., matchIfMissing = true)`** -
   opt-out, not opt-in. Depending on the jar is enough to get the policy.
2. **`@ConditionalOnMissingBean` on every bean** - a service can override one
   piece (a custom publisher, key provider, token validator, or permission
   resolver) without forking anything.
3. **Optional dependencies isolated into their own class**, gated by
   `@ConditionalOnClass`/`@ConditionalOnMissingClass` (bytecode-metadata
   checks, not classloading) - never a try/catch around a directly-imported
   optional type - **but only when the dependency is genuinely incidental**.
   `governance-audit` used to resolve the actor via Spring Security this
   way and was simplified to a plain header instead, removing the
   dependency entirely (see that module's README for why).
   `governance-exception-handling` is the pattern's clean, still-current
   example: its `AccessDeniedException` handler lives in its own class,
   separate from the main handler, loaded only when spring-security-core is
   confirmed present. `security-auth` and `security-rbac`, by contrast,
   depend on `spring-security-core` directly and non-optionally - validating
   tokens and enforcing access decisions against a security context IS their
   purpose, not an incidental integration, so isolating it would be
   pointless ceremony in the other direction.
4. **Shared policy lives in `governance-core`**, not duplicated per module -
   correlation id, actor resolution, and `@Sensitive` masking are defined
   once so every module's output is consistent. `security-auth` and
   `security-rbac` both depend on it too (for actor propagation and
   correlation-id-tagged denial logs respectively), so this sharing spans
   both families.
5. **A dedicated `/actuator/<module>` endpoint per module** - policy is
   inspectable with `curl`, not a question for a spreadsheet during an
   audit. **Judgment call per module on what's safe to expose**: encryption
   and auth endpoints show configuration shape but never secret material
   (key bytes, HMAC secrets); the RBAC endpoint exposes the full
   role-permission map on purpose, since that map *is* the access policy an
   auditor needs to see, not a secret.
6. **Plain domain-name modules, no artificial `-starter` split** - see
   "Naming" above; don't reintroduce a code/dependency-bundle split for the
   next module unless there's an actual reason (e.g. genuinely needing to
   support two swappable implementations under one name).

## Suggested next modules, in priority order

1. `governance-resilience` - mandatory Resilience4j timeout/retry/
   circuit-breaker defaults.
2. `governance-api-standards` - versioning and idempotency-key handling
   (the standard error envelope itself is already covered by
   `governance-exception-handling`).
3. `governance-policy-engine` - OPA/Rego-backed dynamic business rules
   (data residency, transaction limits) that need to change without a
   redeploy.
4. `security-rate-limit` - per-caller/per-key rate limiting, a natural
   companion to `security-auth` (limits keyed off the resolved principal).
5. `security-secrets` - a thin, consistent abstraction over
   KMS/Vault/environment secrets, generalizing the `KeyProvider` pattern
   already established in `security-crypto` beyond just encryption keys.

(`governance-archunit`, `governance-exception-handling`,
`governance-app-response`, `security-crypto`, `security-auth`,
`security-rbac`, and `security-auth-ms-exchange` are already built; see
each module's README for wiring. Note `governance-exception-handling` and
`governance-app-response` are alternatives to each other, not both to be
adopted at once - see either README.)
