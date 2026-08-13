package com.platform.security.rbac.aspect;

import com.roconmachine.governance.core.config.GovernanceCoreProperties;
import com.platform.security.rbac.config.SecurityRbacProperties;
import com.platform.security.rbac.fixtures.SampleService;
import com.platform.security.rbac.resolver.PermissionResolver;
import com.platform.security.rbac.resolver.PropertiesPermissionResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RbacEnforcementAspectTest {

    private SampleService proxiedService;

    @BeforeEach
    void setUp() {
        SecurityRbacProperties properties = new SecurityRbacProperties();
        properties.getRolePermissions().put("PAYMENT_ADMIN", List.of("payment:approve", "payment:read"));
        PermissionResolver resolver = new PropertiesPermissionResolver(properties);

        RbacEnforcementAspect aspect = new RbacEnforcementAspect(resolver, new GovernanceCoreProperties());

        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
        factory.addAspect(aspect);
        proxiedService = factory.getProxy();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsCallWhenCallerHasOneOfTheRequiredRoles() {
        authenticateAs("PAYMENT_ADMIN");
        assertThatCode(() -> proxiedService.adminOnlyAction()).doesNotThrowAnyException();
    }

    @Test
    void deniesCallWhenCallerLacksAnyRequiredRole() {
        authenticateAs("PAYMENT_VIEWER");
        assertThatThrownBy(() -> proxiedService.adminOnlyAction())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deniesCallWhenNoAuthenticationIsPresentAtAll() {
        assertThatThrownBy(() -> proxiedService.adminOnlyAction())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireAllDemandsEveryListedRole() {
        authenticateAs("PAYMENT_ADMIN"); // only one of the two required roles
        assertThatThrownBy(() -> proxiedService.requiresBothRoles())
                .isInstanceOf(AccessDeniedException.class);

        authenticateAs("PAYMENT_ADMIN", "PAYMENT_SUPERVISOR");
        assertThatCode(() -> proxiedService.requiresBothRoles()).doesNotThrowAnyException();
    }

    @Test
    void permissionCheckResolvesViaRolePermissionMapping() {
        authenticateAs("PAYMENT_ADMIN"); // mapped to payment:approve in setUp()
        assertThatCode(() -> proxiedService.approveTransfer()).doesNotThrowAnyException();
    }

    @Test
    void permissionCheckDeniesWhenCallersRoleIsntMappedToThatPermission() {
        authenticateAs("PAYMENT_VIEWER"); // not mapped to anything
        assertThatThrownBy(() -> proxiedService.approveTransfer())
                .isInstanceOf(AccessDeniedException.class);
    }

    private void authenticateAs(String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        var authentication = new UsernamePasswordAuthenticationToken("test-user", null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
