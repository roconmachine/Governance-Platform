# security-rbac

Turns "only PAYMENT_ADMIN can approve transfers over $10k" from a wiki page
into `@RequiresRole`/`@RequiresPermission` annotations enforced by an AOP
aspect - and turns the role-to-permission mapping itself into a versioned
configuration property, so "which roles can approve a transfer" is a
`curl` call and a config diff, not a grep across `hasRole()` calls scattered
through the codebase.

## 1. Add the dependency

```xml
<dependency>
    <groupId>com.platform.security</groupId>
    <artifactId>security-rbac</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

This module does **not** depend on `security-auth`. It only requires *some*
`Authentication` to already be present in Spring Security's context by the
time an annotated method runs - whether that's `security-auth`, your own
auth filter, or Spring Security's standard login mechanisms doesn't matter
to this module at all.

## 2. Simple role checks

```java
@Service
public class PaymentService {

    @RequiresRole({"PAYMENT_ADMIN", "PAYMENT_SUPERVISOR"})
    public void approveTransfer(String transferId) {
        // caller must hold at least one of the listed roles
    }

    @RequiresRole(value = {"PAYMENT_ADMIN", "COMPLIANCE_OFFICER"}, requireAll = true)
    public void releaseFrozenAccount(String accountId) {
        // caller must hold BOTH roles
    }
}
```

Roles are matched against Spring Security's granted authorities as
`ROLE_<role>` - the same convention `security-auth` populates and
`hasRole()`/`@PreAuthorize` expect, so this composes with either.

## 3. Permission checks (recommended for business logic)

```java
@RequiresPermission("payment:approve")
public void approveTransfer(String transferId) { ... }
```

```yaml
security:
  rbac:
    role-permissions:
      PAYMENT_ADMIN:
        - payment:read
        - payment:approve
      PAYMENT_VIEWER:
        - payment:read
    deny-by-default: true   # a role with no entry above grants NOTHING - fail closed
```

The advantage over `@RequiresRole` directly: when a new role needs to
approve transfers, you change the `role-permissions` config once - you
don't hunt down every `@RequiresRole({"PAYMENT_ADMIN", ...})` call site in
the codebase and add the new role to each one.

## 4. What a denial looks like

A failed check throws `org.springframework.security.access.AccessDeniedException`
- the standard Spring Security type, so it flows into whatever
`@ControllerAdvice`/exception handling your service already has mapping it
to a `403`. Nothing new to wire up.

Every denial also logs a `WARN` with the correlation id from
`governance-core`'s MDC (if that module is present):

```
RBAC denial [correlationId=6521ca85-...] PaymentService.approveTransfer
  requires permission [payment:approve] but caller has [payment:read]
```

## 5. Swapping the permission source

```java
@Bean
public PermissionResolver databasePermissionResolver(RoleRepository roleRepository) {
    return role -> roleRepository.findPermissions(role); // DB-backed, admin-UI-editable, etc.
}
```

Replaces `PropertiesPermissionResolver` entirely (`@ConditionalOnMissingBean`)
- useful once the role/permission mapping needs to change without a
redeploy, e.g. via an internal admin tool.

## 6. Governance visibility

```
GET /actuator/securityRbac
```

```json
{
  "rbac": {
    "enabled": true,
    "denyByDefault": true,
    "rolePermissions": {
      "PAYMENT_ADMIN": ["payment:read", "payment:approve"],
      "PAYMENT_VIEWER": ["payment:read"]
    }
  },
  "module": "security-rbac:0.1.0-SNAPSHOT"
}
```

Unlike `security-crypto`/`security-auth`'s endpoints, there's no secret
being withheld here - the role-permission map genuinely **is** the access
policy, and exposing it in full is the point: an auditor should be able to
answer "who can approve a $10k transfer" from a single `curl`, not a code
review.

## Build

```
mvn clean test
```

Tests use `AspectJProxyFactory` to exercise the real aspect against a
fixture service (no Spring context needed for the unit tests) and cover:
role-satisfied vs. role-denied, no-authentication-at-all, `requireAll`
semantics, and permission resolution through the role-permission mapping.
