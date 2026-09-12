# security-auth

Reusable JWT authentication and session-security starter for Spring Boot 3.3+ / Java 21.
Drop it on the classpath, set `jwt.security.secret`, and a host application gets token
issuance, one-time-use refresh token rotation with theft detection, a request filter,
and a default `SecurityFilterChain` — all overridable bean-by-bean.

> Renamed from `jwt-security-spring-boot-starter`. Maven coordinates are now
> `com.roconmachine:security-auth`, base package `com.roconmachine.securityauth`.

## Directory structure

```
security-auth/
├── pom.xml
├── README.md
├── src/main/java/com/roconmachine/securityauth/
│   ├── autoconfigure/
│   │   ├── JwtSecurityProperties.java                  (@ConfigurationProperties)
│   │   ├── JwtSecurityAutoConfiguration.java            (@AutoConfiguration - core beans)
│   │   ├── JwtSecurityFilterChainAutoConfiguration.java (@AutoConfiguration - default chain)
│   │   └── RefreshTokenAutoConfiguration.java           (@AutoConfiguration - rotation)
│   ├── core/
│   │   ├── JwtService.java / JwtServiceImpl.java         (access token issuance/parsing)
│   │   ├── KeyProvider.java / DefaultKeyProvider.java     (signing key)
│   │   ├── RefreshTokenService.java / DefaultRefreshTokenService.java  (rotation + reuse detection)
│   │   ├── RefreshTokenStore.java / InMemoryRefreshTokenStore.java     (rotation storage)
│   │   ├── StoredRefreshToken.java                        (persisted token record)
│   │   └── TokenPair.java                                 (access + refresh token result)
│   ├── filter/
│   │   └── JwtAuthenticationFilter.java                   (OncePerRequestFilter)
│   ├── extension/
│   │   ├── UserAuthenticationProvider.java                (interface - required in prod)
│   │   ├── DefaultUserAuthenticationProvider.java          (dev-only fallback)
│   │   └── ClaimsCustomizer.java                           (interface - optional)
│   └── exception/
│       ├── JwtAuthenticationException.java
│       ├── InvalidRefreshTokenException.java
│       └── TokenReuseDetectedException.java
├── src/main/resources/META-INF/
│   ├── spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   └── additional-spring-configuration-metadata.json
└── src/test/java/com/roconmachine/securityauth/
    ├── JwtServiceImplTest.java
    └── RefreshTokenServiceTest.java
```

## Install

```bash
mvn install
```

```xml
<dependency>
    <groupId>com.roconmachine</groupId>
    <artifactId>security-auth</artifactId>
    <version>1.0.0</version>
</dependency>
```

## `application.yml` in the consuming application

```yaml
jwt:
  security:
    enabled: true
    # Generate with: openssl rand -base64 32
    secret: "c2VjdXJlLXJhbmRvbS0yNTYtYml0LWJhc2U2NC1lbmNvZGVkLXNlY3JldC1rZXk="
    expiration-ms: 3600000              # 1 hour access tokens
    refresh-expiration-ms: 604800000    # 7 day refresh tokens
    issuer: "roconmachine-auth-service"
    header-name: "Authorization"
    header-prefix: "Bearer "
    permit-all-patterns:
      - "/actuator/health/**"
      - "/api/v1/auth/**"
    async-enabled: false
    auto-security-chain-enabled: true       # false if the host app wires its own SecurityFilterChain
    refresh-token-rotation-enabled: true    # false for plain stateless JWT refresh tokens, no tracking
    refresh-token-length-bytes: 32          # entropy of each opaque refresh token
```

`secret` is the only property without a usable default.

## Token lifecycle: refresh token rotation

Access tokens stay stateless JWTs (fast to verify, no DB hit per request). Refresh
tokens are deliberately **not** JWTs — they're opaque, cryptographically random
strings, because rotation and reuse detection both require server-side state that a
stateless JWT can't provide without a separate blacklist anyway. Only the SHA-256 hash
of a refresh token is ever persisted, so a leaked datastore alone can't be used to
mint sessions.

**Flow:**

1. **Login** — call `refreshTokenService.issue(subject)`. Returns a `TokenPair`
   (short-lived access token + a brand-new refresh token, the first in a new
   "rotation family").
2. **Refresh** — call `refreshTokenService.rotate(rawRefreshToken)`. The presented
   refresh token is atomically marked used and a new access/refresh pair is issued in
   the *same* family. The old refresh token can never be redeemed again.
3. **Reuse / theft detection** — if a refresh token that was already rotated is
   presented again, that's a strong signal it was stolen and used out-of-band. The
   service immediately revokes the **entire family** (every token descended from that
   login, including the currently-live one) and throws `TokenReuseDetectedException`.
   Catch this in the host app to force full re-authentication and optionally alert the
   user.
4. **Logout** — call `refreshTokenService.revoke(rawRefreshToken)` to kill that
   session's family on demand.

```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RefreshTokenService refreshTokenService;

    public AuthController(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/login")
    public TokenPair login(@RequestBody LoginRequest request) {
        // ... verify credentials against your user store first ...
        return refreshTokenService.issue(request.username());
    }

    @PostMapping("/refresh")
    public TokenPair refresh(@RequestBody RefreshRequest request) {
        return refreshTokenService.rotate(request.refreshToken());
        // TokenReuseDetectedException / InvalidRefreshTokenException bubble up to
        // your @ExceptionHandler - map both to 401 and end the client-side session.
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
```

```java
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler({InvalidRefreshTokenException.class, TokenReuseDetectedException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRefresh(RuntimeException ex) {
        if (ex instanceof TokenReuseDetectedException reuse) {
            // e.g. audit-log this and notify the user - their session may have been stolen
            log.warn("Refresh token reuse for subject [{}]", reuse.getSubject());
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Session invalid, please log in again"));
    }
}
```

### Production storage

The bundled `InMemoryRefreshTokenStore` is dev-only — it logs a warning on startup and
loses all state on restart, and doesn't work at all across multiple instances. Override
it with a `@Bean RefreshTokenStore` backed by Redis (ideal: put a TTL on each key equal
to `refresh-expiration-ms`) or a database table keyed on `tokenHash`:

```java
@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {
    // save() -> SET tokenHash -> serialized StoredRefreshToken, with EXPIRE at expiresAt
    // findByHash() -> GET
    // update() -> SET (overwrite)
    // revokeFamily() -> maintain a Set<tokenHash> per familyId (or a separate
    //                    "revoked-families" set) so isFamilyRevoked() is O(1)
}
```

### If you don't want rotation at all

Set `jwt.security.refresh-token-rotation-enabled: false`. `RefreshTokenAutoConfiguration`
won't register, and `JwtService.generateRefreshToken(subject)` remains available for a
plain stateless refresh JWT with no server-side tracking.

## Minimal usage — access tokens only

```java
@Autowired JwtService jwtService;

String accessToken = jwtService.generateToken("user-123", Map.of("role", "ADMIN"));
boolean valid = jwtService.isTokenValid(accessToken, "user-123");
```

Requests then need `Authorization: Bearer <token>` — `JwtAuthenticationFilter` handles
the rest automatically once it's on the classpath.

## Required extension: `UserAuthenticationProvider`

The bundled `DefaultUserAuthenticationProvider` trusts token claims with **no**
user-store lookup and logs a warning every time it runs. Override it in any real
deployment:

```java
@Component
public class DatabaseUserAuthenticationProvider implements UserAuthenticationProvider {

    private final UserRepository userRepository;

    public DatabaseUserAuthenticationProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUser(String subject, Map<String, Object> claims) {
        return userRepository.findByUsername(subject)
                .filter(User::isActive)
                .map(this::toUserDetails)
                .orElse(null); // null => request stays unauthenticated
    }

    private UserDetails toUserDetails(User user) {
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password("")
                .authorities(user.getRoles().stream().map(SimpleGrantedAuthority::new).toList())
                .build();
    }
}
```

## Optional extension: `ClaimsCustomizer`

```java
@Component
@Order(1)
public class TenantClaimsCustomizer implements ClaimsCustomizer {

    @Override
    public void customize(JwtBuilder builder, String subject) {
        builder.claim("tenant_id", TenantContext.currentTenantId());
    }
}
```

## Optional extension: `KeyProvider`

```java
@Component
public class VaultKeyProvider implements KeyProvider {

    private final SecretKey key;

    public VaultKeyProvider(VaultTemplate vaultTemplate) {
        byte[] secretBytes = vaultTemplate.read("secret/jwt-signing-key")
                .getData().get("key").toString().getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    @Override
    public SecretKey getSigningKey() {
        return key;
    }
}
```

## Overriding the default `SecurityFilterChain`

```java
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                     JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**", "/actuator/health/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

Or set `jwt.security.auto-security-chain-enabled: false` to skip the starter's chain
entirely.

## Design notes

- **Access vs. refresh tokens are different token types on purpose**: access tokens
  are stateless JWTs for fast, DB-free verification on every request; refresh tokens
  are opaque and server-tracked because rotation and theft detection are fundamentally
  stateful problems.
- **Thread safety**: `JwtServiceImpl` and `DefaultRefreshTokenService` hold no mutable
  state beyond immutable/injected collaborators, so one Spring-managed singleton of
  each safely serves all request threads — covered by concurrency and rotation tests.
- **Fail-open filter, fail-closed authorization**: `JwtAuthenticationFilter` never
  itself rejects a request; an invalid/missing token just leaves the `SecurityContext`
  empty, and Spring Security's `authorizeHttpRequests` rules are what return 401/403.
- **Every bean is `@ConditionalOnMissingBean`**: override exactly the piece you need
  (`KeyProvider`, `UserAuthenticationProvider`, `RefreshTokenStore`, the whole
  `SecurityFilterChain`, etc.) without forking or disabling the rest of the starter.
