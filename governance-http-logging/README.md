# governance-http-logging

Captures every HTTP request/response pair - method, path, status, duration,
headers, and (opt-in) body - using the same correlation id and `@Sensitive`
masking policy as the rest of the governance suite. Kept separate from
`governance-audit` on purpose: see the "why a separate module"
section below.

## 1. Add the dependency

```xml
<dependency>
    <groupId>com.platform.governance</groupId>
    <artifactId>governance-http-logging</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

`governance-core` comes along transitively. That's the whole integration -
every request is captured from here on, opt-out not opt-in.

## 2. What you get, out of the box

```
HTTP_ACCESS logger: HttpAccessLogEvent{correlationId='...', actor='...',
  method='POST', path='/api/transfer', queryString='status=active',
  requestParameters={status=active}, status=200, durationMillis=42,
  requestHeaders={...}, responseHeaders={...}, timestamp=...}
```

- `/actuator/health`, `/actuator/**`, `/favicon.ico` excluded by default
- `Authorization`, `Cookie`, `Set-Cookie`, `X-Api-Key` headers masked
  (`FULL` strategy) regardless of `includeHeaders`
- **Query string and request parameters are captured by default**
  (`includeParams: true`) - both the raw query string and the parsed
  parameter map (query string + form-encoded body params, per the servlet
  spec). Sensitive parameter names (`cardNumber`, `pin`, `token`, etc. - see
  `sensitiveParameterNames`) are masked the same way sensitive headers are.
  Set `include-params: false` to turn this off entirely.
- Request/response **bodies are off by default** - the highest PII/PCI risk
  surface. Turn on deliberately, per environment, not fleet-wide.
- **Publishing is asynchronous by default** (`async: true`) - the actual
  `HttpAccessLogPublisher.publish()` call is handed off to a bounded
  executor so a slow log sink never adds latency to the response, using the
  same `MdcTaskDecorator` + `CallerRunsPolicy` fail-safe shape used
  throughout this platform (see `governance-exception-handling`'s async
  logging for the full mechanics). Set `async: false` to force synchronous
  publishing - the event is guaranteed published before the response
  completes, at the cost of adding the publish call to request latency.

## 3. Turning on body capture safely

```yaml
governance:
  http-logging:
    include-body: true
    max-body-size: 2000                 # characters, bounds log line size
    sensitive-body-fields:              # name-based regex masking - see caveat below
      - cardNumber
      - pin
      - cvv
      - password
```

**Caveat, stated plainly:** body masking here is name-based regex matching
on `"field":"value"` pairs, not a real JSON parse. It's a safety net for
common flat-shape payloads, not a guarantee for deeply nested or array
JSON. For anything handling card data or equivalent, prefer keeping
`include-body: false` and relying on `@Auditable`/`@Sensitive` on your
actual DTOs in `governance-audit`, which masks real object fields
rather than pattern-matching text.

## 4. Sampling for high-throughput services

```yaml
governance:
  http-logging:
    sample-rate: 0.1   # log ~10% of requests; excluded-paths are still always skipped
```

Lets a payment-processing service under heavy load keep the policy "on"
without the full logging volume/cost.

## 5. Governance is inspectable at runtime

```
GET /actuator/governanceHttpLogging
```

```json
{
  "httpLogging": {
    "enabled": true,
    "includeHeaders": true,
    "includeBody": false,
    "maxBodySize": 2000,
    "excludedPaths": ["/actuator/**", "/health", "/favicon.ico"],
    "sensitiveHeaders": ["Authorization", "Cookie", "Set-Cookie", "X-Api-Key"],
    "sensitiveBodyFields": ["cardNumber", "pan", "cvv", "cvv2", "pin", "password", "ssn", "nationalId", "accountNumber"],
    "sampleRate": 1.0
  },
  "module": "governance-http-logging:0.1.0-SNAPSHOT",
  "seeAlso": "/actuator/governance (shared correlation-id/actor/masking policy)"
}
```

## Why a separate module, not part of governance-audit

| | `@Auditable` (governance-audit) | HTTP access logging (governance-http-logging) |
|---|---|---|
| Granularity | One event per governed *business action* | One event per HTTP call, including health checks/retries |
| Volume | Low | High - typically 10-100x |
| Default posture | Always on, non-negotiable | On, but sampling/body-capture are levers teams need per-environment |
| Consumer | Compliance/audit | SRE/security/debugging |
| PII risk | Bounded by which DTOs you annotate | Higher - raw headers/bodies unless carefully masked |

Splitting them means a service can dial HTTP-logging sampling down under
load without touching the audit trail, and vice versa - one policy doesn't
have to compromise for the other's constraints.

## Build

```
mvn clean verify
```
