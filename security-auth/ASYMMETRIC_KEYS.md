# Asymmetric Key Support Configuration Guide

The `security-auth` module now supports asymmetric JWT validation in addition to the original HMAC support. This enables integration with external OAuth2 identity providers like Keycloak, Auth0, and others.

## Configuration Modes

### 1. HMAC Validation (Symmetric Keys) - Default

Use for internal single-issuer platforms with shared secrets.

```yaml
security:
  auth:
    enabled: true
    hmac-secret: "base64-encoded-shared-secret"
    issuer: "your-internal-issuer"
    audience: "your-app"
```

**Pros:** Simple, no external dependencies
**Cons:** Secret must be shared with token issuer, no key rotation

---

### 2. JWKS Endpoint (Remote Key Fetching) - Recommended for External Providers

Use for tokens issued by external identity providers. Keys are automatically fetched and cached from the provider's JWKS endpoint.

```yaml
security:
  auth:
    enabled: true
    jwks-uri: "http://keycloak:8080/realms/myrealm/protocol/openid-connect/certs"
    issuer: "http://keycloak:8080/realms/myrealm"
    audience: "my-app-client-id"
    roles-claim: "roles"  # or "client_roles", depending on Keycloak config
    jwks-cache-duration-seconds: 300  # Cache keys for 5 minutes
```

**How it works:**
1. Token arrives with `kid` (Key ID) in JWT header
2. `AsymmetricJwtValidator` fetches keys from JWKS endpoint (cached)
3. Validates signature using the matching public key
4. Validates issuer and audience claims
5. Extracts roles from configured claim

**Keycloak Example:**
```yaml
security:
  auth:
    jwks-uri: "http://localhost:8080/realms/demo/protocol/openid-connect/certs"
    issuer: "http://localhost:8080/realms/demo"
    audience: "my-service"
    roles-claim: "roles"  # Default Keycloak realm roles claim
```

**Environment Variables (Docker/Kubernetes):**
```yaml
security:
  auth:
    jwks-uri: ${OAUTH2_JWKS_URI}
    issuer: ${OAUTH2_ISSUER}
    audience: ${OAUTH2_AUDIENCE}
```

**Pros:** 
- Automatic key rotation
- No secrets to manage
- Scalable for multiple identity providers

**Cons:** 
- Requires network access to JWKS endpoint
- Network latency (mitigated by caching)

---

### 3. Static PEM Public Key (Local Key)

Use for tokens issued by external providers when you prefer not to fetch keys dynamically.

```yaml
security:
  auth:
    enabled: true
    public-key-pem: |
      -----BEGIN PUBLIC KEY-----
      MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
      -----END PUBLIC KEY-----
    issuer: "external-issuer"
    audience: "my-app"
```

**Pros:** 
- No network dependency
- Simple for single static key

**Cons:** 
- Manual key rotation required
- Doesn't support key rotation/versioning

---

## Key Selection Logic

The module automatically selects the validator based on configuration (in order):

1. **JWKS Endpoint** - if `security.auth.jwks-uri` is configured
2. **Static PEM Key** - if `security.auth.public-key-pem` is configured
3. **HMAC Secret** - if `security.auth.hmac-secret` is configured
4. **Error** - if none are configured

**Important:** Only configure ONE of these. Mixing them is not supported.

---

## Example: Keycloak Integration

### Step 1: Get Keycloak JWKS URI

1. Access Keycloak Admin Console
2. Navigate to: `Realm Settings` → `General` → `Endpoints`
3. Copy the JWKS endpoint URL (e.g., `http://keycloak:8080/realms/demo/protocol/openid-connect/certs`)

### Step 2: Configure Spring Application

```yaml
security:
  auth:
    enabled: true
    jwks-uri: "http://keycloak:8080/realms/demo/protocol/openid-connect/certs"
    issuer: "http://keycloak:8080/realms/demo"
    audience: "my-service-client"
    roles-claim: "roles"  # For realm roles
    # OR use "resource_access.my-service-client.roles" for client-specific roles
    clock-skew-seconds: 30
    reject-invalid-token: true
```

### Step 3: Handle Client-Specific Roles (Optional)

If Keycloak is configured with **client-specific roles** instead of realm roles, you need custom role extraction. The default `roles-claim` won't work.

The current implementation extracts from a single claim. For complex claim structures, you may need to:
- Use Keycloak token mappers to flatten roles into a top-level `roles` claim
- Or extend `AsymmetricJwtValidator` to handle nested claim paths

---

## Advanced Configuration

### Clock Skew
When server clocks are slightly out of sync:

```yaml
security:
  auth:
    clock-skew-seconds: 60  # Default: 30 seconds
```

### Key Cache Duration
Control how long JWKS keys are cached before refreshing:

```yaml
security:
  auth:
    jwks-cache-duration-seconds: 600  # 10 minutes (default: 300)
```

### Reject Invalid Tokens
Control behavior when a token is present but invalid:

```yaml
security:
  auth:
    reject-invalid-token: true   # Return 401 (default)
    # or
    reject-invalid-token: false  # Proceed unauthenticated
```

---

## Accessing the Authenticated User

```java
import com.roconmachine.security.auth.model.CurrentUser;
import com.roconmachine.security.auth.model.AuthenticatedPrincipal;

@GetMapping("/profile")
public void getUserProfile() {
    Optional<AuthenticatedPrincipal> principal = CurrentUser.get();
    
    if (principal.isPresent()) {
        String userId = principal.get().getSubject();
        Set<String> roles = principal.get().getRoles();
        Map<String, Object> allClaims = principal.get().getClaims();
        
        System.out.println("User: " + userId);
        System.out.println("Roles: " + roles);
    }
}
```

**All HTTP endpoints automatically have authentication populated in Spring Security's `SecurityContext`:**

```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
String userId = auth.getName();  // principal.getSubject()
Set<? extends GrantedAuthority> authorities = auth.getAuthorities();  // roles as authorities
```

---

## Troubleshooting

### "JWKS endpoint returned status 401/403"
- Check JWKS endpoint URL is correct
- Verify firewall/network connectivity
- Check if endpoint requires authentication

### "Key not found for kid: xyz"
- Token header has `kid` that doesn't exist in JWKS
- JWKS cache may be stale; check `jwks-cache-duration-seconds`
- Verify JWKS endpoint contains expected keys

### "Token issuer did not match expected issuer"
- `security.auth.issuer` must exactly match the token's `iss` claim
- Check Keycloak realm URL in issuer config

### "Token audience did not match expected audience"
- `security.auth.audience` must match the token's `aud` claim
- For Keycloak, this is the client ID

### "No token validator configuration found"
- Must configure one of: `hmac-secret`, `jwks-uri`, or `public-key-pem`

---

## RSA Key Support

Currently supported: **RSA keys** (RSA256, RSA384, RSA512)

Not yet supported: **EC keys** (ECDSA256, ECDSA384, ECDSA512)
- EC support requires BouncyCastle library
- Most OAuth2 providers default to RSA, so this is rarely an issue
- Future versions may add EC support

---

## Performance Considerations

### JWKS Caching
- Keys are cached in-memory for the configured duration
- Default cache: 300 seconds (5 minutes)
- Each token arrival checks cache before fetching
- Cache is per-instance; distributed systems have independent caches

### Recommended Settings
- Production: `jwks-cache-duration-seconds: 600` (10 minutes)
- High-frequency key rotation: `jwks-cache-duration-seconds: 60` (1 minute)
- Development: Can reduce further if testing key rotation

---

## Migration from HMAC to Asymmetric Keys

```yaml
# Before (HMAC)
security:
  auth:
    hmac-secret: "base64secret"

# After (Keycloak JWKS)
security:
  auth:
    jwks-uri: "http://keycloak:8080/realms/myrealm/protocol/openid-connect/certs"
    issuer: "http://keycloak:8080/realms/myrealm"
```

No code changes required. The `CurrentUser.get()` API remains the same.
