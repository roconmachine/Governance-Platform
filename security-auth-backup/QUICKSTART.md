# Security-Auth: Quick Start Guide

## 5-Minute Setup with Keycloak

### 1. Add Dependency
Your service already has `security-auth` as a dependency:
```xml
<dependency>
    <groupId>com.roconmachine</groupId>
    <artifactId>security-auth</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Configure Application
Add to `application.yml`:

```yaml
security:
  auth:
    enabled: true
    jwks-uri: "http://keycloak:8080/realms/master/protocol/openid-connect/certs"
    issuer: "http://keycloak:8080/realms/master"
    audience: "my-service-client"
    roles-claim: "roles"
```

**For development/testing:**
```yaml
security:
  auth:
    enabled: true
    jwks-uri: "http://localhost:8080/realms/demo/protocol/openid-connect/certs"
    issuer: "http://localhost:8080/realms/demo"
    audience: "my-service"
    roles-claim: "roles"
```

### 3. Get Keycloak Details
1. Go to Keycloak Admin Console
2. Navigate to your realm
3. Go to "Realm Settings" → "General" → "Endpoints"
4. Copy the JWKS endpoint URL
5. Your issuer is the same URL without `/protocol/openid-connect/certs`

**Example:**
- **JWKS Endpoint:** `http://keycloak:8080/realms/master/protocol/openid-connect/certs`
- **Issuer:** `http://keycloak:8080/realms/master`

### 4. Use in Your Code
```java
import com.roconmachine.security.auth.model.CurrentUser;
import com.roconmachine.security.auth.model.AuthenticatedPrincipal;

@GetMapping("/profile")
public ResponseEntity<?> getProfile() {
    var principal = CurrentUser.get();
    
    if (principal.isPresent()) {
        AuthenticatedPrincipal user = principal.get();
        return ResponseEntity.ok(Map.of(
            "userId", user.getSubject(),
            "roles", user.getRoles(),
            "claims", user.getClaims()
        ));
    }
    
    return ResponseEntity.status(401).build();
}
```

### 5. Send JWT in Requests
All API requests must include the token:
```bash
curl -H "Authorization: Bearer <jwt-token>" http://localhost:8080/profile
```

The module automatically:
- ✅ Validates the token signature (using Keycloak's public key)
- ✅ Checks token expiration
- ✅ Verifies issuer matches expected Keycloak realm
- ✅ Verifies audience matches your service
- ✅ Extracts roles and populates Spring Security's context
- ✅ Logs authenticated user in audit trail (via governance-core)

---

## Common Scenarios

### Scenario 1: Token Already Present in Other Services
If your service consumes tokens from services that already validate them, no additional configuration needed. The token is already in the HTTP header.

### Scenario 2: Locally Issued Test Tokens
For testing without Keycloak:
```yaml
security:
  auth:
    enabled: false  # Disable validation for testing
```

Or keep enabled and use a static PEM key:
```yaml
security:
  auth:
    public-key-pem: |
      -----BEGIN PUBLIC KEY-----
      MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
      -----END PUBLIC KEY-----
    issuer: "test-issuer"
```

### Scenario 3: Custom Roles Handling
If Keycloak is configured with non-standard role claims, set:
```yaml
security:
  auth:
    roles-claim: "custom_roles"  # or whatever your claim name is
```

If roles are nested (e.g., `resource_access.my-app.roles`):
- This requires custom code to extract
- See `ASYMMETRIC_KEYS.md` for details

---

## Debugging

### Check if module is active:
```java
import org.springframework.security.core.context.SecurityContextHolder;

@GetMapping("/debug/auth")
public Map<String, Object> debug() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return Map.of(
        "authenticated", auth != null && auth.isAuthenticated(),
        "principal", auth != null ? auth.getPrincipal() : "null",
        "authorities", auth != null ? auth.getAuthorities() : []
    );
}
```

### Enable debug logging:
```yaml
logging:
  level:
    com.roconmachine.security.auth: DEBUG
```

### Common Issues:

**"No token found" or "Unauthenticated"**
- Check Authorization header is present: `Authorization: Bearer <token>`
- Check header name matches config (default: `Authorization`)
- Check header prefix matches config (default: `Bearer `)

**"Token validation failed"**
- Token may be expired
- Token signature may not match Keycloak's key
- Check Keycloak realm configuration

**"Token issuer did not match"**
- Config `issuer` must match token's `iss` claim exactly
- Check both URLs use same protocol/domain

**"Token audience did not match"**
- Config `audience` must match token's `aud` claim
- For Keycloak, this is typically the client ID

---

## Switching from HMAC to Keycloak

### Before (HMAC - symmetric keys):
```yaml
security:
  auth:
    hmac-secret: "shared-secret-with-key-issuer"
```

### After (Keycloak - asymmetric keys):
```yaml
security:
  auth:
    jwks-uri: "http://keycloak:8080/realms/master/protocol/openid-connect/certs"
    issuer: "http://keycloak:8080/realms/master"
    audience: "my-service-client"
```

**No code changes needed!** The API remains identical.

---

## Production Checklist

- [ ] JWKS endpoint is reachable from your service
- [ ] Firewall rules allow HTTP to Keycloak
- [ ] `jwks-uri` points to correct Keycloak realm
- [ ] `issuer` exactly matches Keycloak realm URL
- [ ] `audience` matches client ID in Keycloak
- [ ] Roles claim name matches Keycloak configuration
- [ ] Clock skew is reasonable (30-60 seconds typical)
- [ ] Token validation is NOT set to reject invalid (or ensure all endpoints send tokens)
- [ ] Test with valid token: tokens work
- [ ] Test with expired token: request is rejected
- [ ] Test without token: request is rejected or unauthenticated

---

## Reference Links

- **Full Configuration Guide:** [ASYMMETRIC_KEYS.md](ASYMMETRIC_KEYS.md)
- **Implementation Details:** [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)
- **Configuration Examples:** [example-config.yaml](example-config.yaml)
- **Keycloak Docs:** https://www.keycloak.org/docs/latest/server_admin/
- **JJWT (JWT Library):** https://github.com/jwtk/jjwt

---

## Still Using HMAC?

The original HMAC validator still works unchanged:
```yaml
security:
  auth:
    hmac-secret: "base64-encoded-secret"
    issuer: "my-issuer"
    audience: "my-service"
```

Migration to asymmetric keys is optional and can happen at your own pace.
