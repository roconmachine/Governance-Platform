# security-auth-ms-exchange

A Spring Boot 3.x auto-configuring library for OAuth2 authentication against
Microsoft Exchange Server (Microsoft Entra ID / Exchange Online) - Client
Credentials (app-only) and On-Behalf-Of (delegated) token acquisition, built
on the official MSAL4J library, with production-grade token caching and
token validation included.

## 1. Add the dependency

```xml
<dependency>
    <groupId>com.platform.security</groupId>
    <artifactId>security-auth-ms-exchange</artifactId>
    <version>1.0.0</version>
</dependency>
```

`governance-core` comes along transitively (used only for correlation-id-
tagged error logging - no required action on your part).

> **Versioning note:** this module is versioned `1.0.0` rather than the
> `0.1.0-SNAPSHOT` every sibling module in this platform uses. That was
> specified explicitly for this module - worth a deliberate decision on
> your end (e.g. is this genuinely release-ready, or should it also start
> at `0.1.0-SNAPSHOT` until it's exercised against a real tenant?) rather
> than an oversight to silently correct.

## 2. Configure - sample `application.yml`

```yaml
exchange:
  auth:
    enabled: true

    # --- Required: your Entra ID app registration ---
    tenant-id: "11111111-2222-3333-4444-555555555555"   # or a verified domain, e.g. "contoso.onmicrosoft.com"
    client-id: "66666666-7777-8888-9999-000000000000"

    # --- Credential: EXACTLY ONE of the two blocks below ---

    # Option A - client secret (simplest; rotate regularly via Key Vault / your secret manager)
    client-secret: "${EXCHANGE_CLIENT_SECRET}"           # never hardcode - inject via env var or a secrets manager

    # Option B - client certificate (recommended for anything handling real data)
    # certificate:
    #   path: "/etc/secrets/exchange-client-cert.p12"
    #   password: "${EXCHANGE_CERT_PASSWORD}"
    #   alias: "exchange-client"                          # optional - first alias used if omitted

    # --- Scopes requested for the Client Credentials flow ---
    scopes:
      - "https://graph.microsoft.com/.default"            # Microsoft Graph app-only access
      # - "https://outlook.office365.com/.default"         # Exchange Online (EWS/legacy) app-only access

    # --- Caching ---
    cache-enabled: true
    cache:
      max-size: 500
      expiry-buffer-seconds: 300      # refresh 5 minutes before a token's ACTUAL expiry

    # --- Token validation (validateToken()) ---
    validation:
      expected-audience: "api://66666666-7777-8888-9999-000000000000"   # set this in any real deployment

    # --- Sovereign cloud override (omit for public Azure/Entra) ---
    # authority-host: "https://login.microsoftonline.us/"   # US Gov cloud example

    connect-timeout-millis: 10000
    read-timeout-millis: 10000
```

**Azure/Entra portal setup, briefly:** register an app under
*App registrations*, note the *Application (client) ID* and *Directory
(tenant) ID*, then either create a *client secret* (Certificates & secrets)
or upload a certificate's public key there and keep the private key +
certificate together in a PKCS12 file for this module. Grant the app the
relevant **Application permissions** (not delegated) for Client Credentials
- e.g. `Mail.Read` (Application) for Graph - and have an admin grant consent.

## 3. Usage - Client Credentials flow (service-to-service)

```java
import com.platform.security.exchange.service.ExchangeAuthProvider;
import com.platform.security.exchange.exception.TokenAcquisitionException;

@Service
public class MailboxSyncService {

    private final ExchangeAuthProvider exchangeAuthProvider;
    private final RestClient restClient; // or WebClient, OkHttp, whatever your service uses

    public MailboxSyncService(ExchangeAuthProvider exchangeAuthProvider, RestClient restClient) {
        this.exchangeAuthProvider = exchangeAuthProvider;
        this.restClient = restClient;
    }

    public void syncMailbox(String userPrincipalName) {
        String accessToken;
        try {
            accessToken = exchangeAuthProvider.getAccessToken(); // cached after the first call
        } catch (TokenAcquisitionException e) {
            // e.getCause() is the underlying MsalException with Microsoft's own
            // diagnostic detail (error codes, correlation id, claims challenges).
            throw new MailboxSyncFailedException("Could not authenticate to Graph", e);
        }

        restClient.get()
                .uri("https://graph.microsoft.com/v1.0/users/{upn}/messages", userPrincipalName)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(MessagesResponse.class);
    }
}
```

## 4. Usage - On-Behalf-Of flow (delegated access)

For a middleware endpoint that receives a user's own bearer token (e.g.
validated by `security-auth`) and needs to call Exchange/Graph **as that
user**, not as the app itself:

```java
@RestController
public class UserCalendarController {

    private final ExchangeAuthProvider exchangeAuthProvider;

    public UserCalendarController(ExchangeAuthProvider exchangeAuthProvider) {
        this.exchangeAuthProvider = exchangeAuthProvider;
    }

    @GetMapping("/me/calendar")
    public CalendarResponse getMyCalendar(
            @RequestHeader("Authorization") String authorizationHeader) {

        String inboundUserToken = authorizationHeader.replaceFirst("^Bearer ", "");
        String downstreamToken = exchangeAuthProvider.getAccessTokenOnBehalfOf(inboundUserToken);

        // call Graph/Exchange with `downstreamToken`, scoped to this specific user
        ...
    }
}
```

Your app registration needs the **delegated** permission for whatever Graph/
Exchange endpoint you're calling (e.g. `Calendars.Read` delegated), with
admin or user consent granted, for OBO to succeed.

## 5. Usage - validating a token

```java
if (!exchangeAuthProvider.validateToken(someToken)) {
    throw new AccessDeniedException("Invalid or expired token");
}
```

Set `exchange.auth.validation.expected-audience` in any real deployment -
without it, `validateToken()` only proves "signed by this tenant", not
"intended for this application" (see `ExchangeAuthProperties.Validation`'s
Javadoc).

## 6. What's actually happening under the hood

- **`IConfidentialClientApplication`** (MSAL4J) is built once at startup and
  reused as a singleton bean - MSAL4J documents this object as thread-safe
  and explicitly NOT meant to be reconstructed per request.
- **Token caching is per-token, not a fixed TTL.** `TokenCacheManager` ties
  each cached token's expiry to the *actual* `expiresOn` MSAL4J returned for
  that specific token, minus a configurable safety buffer (default 5
  minutes) - never a guessed, hardcoded lifetime. See that class's Javadoc.
- **OBO results are cached per calling user**, keyed by a SHA-256 hash of
  the user assertion token (never the raw token itself, to avoid holding
  sensitive material as a cache key visible in heap dumps or cache
  introspection).
- **`validateToken()` never throws** - any failure (expired, bad signature,
  wrong audience, network error reaching the JWKS endpoint) returns `false`.
  The specific reason is logged server-side at DEBUG only; a caller of this
  boolean method gets no hint of which check failed - the same
  don't-help-an-attacker-probe-your-auth philosophy `security-auth` uses.
- **Failures are two distinct exception types**: `TokenAcquisitionException`
  (Entra rejected the request, or is unreachable - the MSAL4J `MsalException`
  is preserved as the cause with its rich diagnostics) vs. the parent
  `ExchangeAuthException` (configuration/setup problems - missing tenant id,
  neither credential type configured, a certificate that won't load). Catch
  the more specific type if you want to distinguish "your setup is wrong"
  from "Entra rejected this particular request."

## 7. Swapping pieces

Every bean is `@ConditionalOnMissingBean`:

```java
@Bean
public TokenCacheManager tokenCacheManager(ExchangeAuthProperties properties) {
    return new RedisBackedTokenCacheManager(properties, redisTemplate);
    // e.g. for a multi-instance deployment sharing acquired tokens across pods
}
```

## 8. Governance visibility

```
GET /actuator/exchangeAuth
```

```json
{
  "exchangeAuth": {
    "enabled": true,
    "tenantId": "11111111-2222-3333-4444-555555555555",
    "clientId": "66666666-7777-8888-9999-000000000000",
    "credentialType": "CLIENT_SECRET",
    "scopes": ["https://graph.microsoft.com/.default"],
    "cacheEnabled": true,
    "authorityHost": "https://login.microsoftonline.com/",
    "cache": { "maxSize": 500, "expiryBufferSeconds": 300 },
    "validation": { "expectedAudienceConfigured": true }
  },
  "module": "security-auth-ms-exchange:1.0.0"
}
```

`tenantId`/`clientId` are not secrets (Entra treats both as public
identifiers for an app registration) - safe to expose. `credentialType`
tells you WHICH credential mechanism is active without ever exposing the
secret value or certificate password.

## 9. Thread safety

Every component in this module is safe for concurrent use from multiple
request threads with no external synchronization:

- `IConfidentialClientApplication` - documented thread-safe by MSAL4J.
- `TokenCacheManager` - backed by Caffeine's `Cache`, thread-safe by design.
- `EntraTokenValidator` - stateless apart from Nimbus's `RemoteJWKSet` /
  `DefaultJWTProcessor`, both thread-safe.
- `DefaultExchangeAuthProvider` - holds no mutable state of its own.

## Build

```
mvn clean test
```

Tests cover: token caching returns a cached value without re-acquiring,
acquisition failures wrap correctly into `TokenAcquisitionException`, OBO
results are cached per-user (not shared across different callers), blank
user assertions are rejected, the cache's per-token expiry genuinely tracks
each token's own `expiresAt` rather than a fixed TTL, and the validator
fails closed (returns `false`, never throws) on malformed input without
requiring network access.

**Honestly scoped, not overclaimed:** full signature-verification testing
for `validateToken()` requires either a live Entra tenant or a local JWKS
stub server - out of place in a fast unit test, so it isn't included here.
What's tested is the fail-safe contract that matters most for a boolean
validation method: malformed input never throws, it returns `false`.
