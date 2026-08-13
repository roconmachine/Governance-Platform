package com.platform.security.rbac.aspect;

import com.roconmachine.governance.core.config.GovernanceCoreProperties;
import com.platform.security.rbac.annotation.RequiresPermission;
import com.platform.security.rbac.annotation.RequiresRole;
import com.platform.security.rbac.resolver.PermissionResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enforces {@link RequiresRole} and {@link RequiresPermission} against
 * whatever Authentication is currently in Spring Security's context - this
 * aspect has no dependency on security-auth or any other specific mechanism
 * that populates it; it only reads {@link SecurityContextHolder}.
 *
 * A denial throws {@link AccessDeniedException} (standard Spring Security
 * type - your existing exception handling / @ControllerAdvice already maps
 * it to 403) and logs a WARN with the correlation id from governance-core's
 * MDC, so denials are traceable without this module taking a dependency on
 * governance-audit specifically.
 */
@Aspect
public class RbacEnforcementAspect {

    private static final Logger log = LoggerFactory.getLogger(RbacEnforcementAspect.class);

    private final PermissionResolver permissionResolver;
    private final GovernanceCoreProperties coreProperties;

    public RbacEnforcementAspect(PermissionResolver permissionResolver, GovernanceCoreProperties coreProperties) {
        this.permissionResolver = permissionResolver;
        this.coreProperties = coreProperties;
    }

    @Around("@annotation(com.platform.security.rbac.annotation.RequiresRole)")
    public Object enforceRole(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        RequiresRole annotation = method.getAnnotation(RequiresRole.class);

        Set<String> callerRoles = currentRoles();
        Set<String> requiredRoles = Set.of(annotation.value());

        boolean satisfied = annotation.requireAll()
                ? callerRoles.containsAll(requiredRoles)
                : requiredRoles.stream().anyMatch(callerRoles::contains);

        if (!satisfied) {
            denyAndLog(method, "role", requiredRoles, callerRoles);
        }
        return joinPoint.proceed();
    }

    @Around("@annotation(com.platform.security.rbac.annotation.RequiresPermission)")
    public Object enforcePermission(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);

        Set<String> callerRoles = currentRoles();
        Set<String> callerPermissions = callerRoles.stream()
                .flatMap(role -> permissionResolver.permissionsForRole(role).stream())
                .collect(Collectors.toSet());
        Set<String> requiredPermissions = Set.of(annotation.value());

        boolean satisfied = annotation.requireAll()
                ? callerPermissions.containsAll(requiredPermissions)
                : requiredPermissions.stream().anyMatch(callerPermissions::contains);

        if (!satisfied) {
            denyAndLog(method, "permission", requiredPermissions, callerPermissions);
        }
        return joinPoint.proceed();
    }

    private Set<String> currentRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.startsWith("ROLE_") ? authority.substring(5) : authority)
                .collect(Collectors.toSet());
    }

    private void denyAndLog(Method method, String checkType, Set<String> required, Set<String> actual) {
        String correlationId = MDC.get(coreProperties.getMdcKey());
        log.warn("RBAC denial [correlationId={}] {}.{} requires {} {} but caller has {}",
                correlationId, method.getDeclaringClass().getSimpleName(), method.getName(),
                checkType, required, actual);
        throw new AccessDeniedException("Access denied: requires " + checkType + " " + required);
    }
}
