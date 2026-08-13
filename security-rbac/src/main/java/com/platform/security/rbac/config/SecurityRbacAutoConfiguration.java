package com.platform.security.rbac.config;

import com.roconmachine.governance.core.config.GovernanceCoreAutoConfiguration;
import com.roconmachine.governance.core.config.GovernanceCoreProperties;
import com.platform.security.rbac.aspect.RbacEnforcementAspect;
import com.platform.security.rbac.endpoint.SecurityRbacInfoEndpoint;
import com.platform.security.rbac.resolver.PermissionResolver;
import com.platform.security.rbac.resolver.PropertiesPermissionResolver;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Opt-out by default, same pattern as every other module in this suite. */
@AutoConfiguration
@AutoConfigureAfter(GovernanceCoreAutoConfiguration.class)
@EnableConfigurationProperties(SecurityRbacProperties.class)
@ConditionalOnProperty(prefix = "security.rbac", name = "enabled", matchIfMissing = true)
public class SecurityRbacAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PermissionResolver permissionResolver(SecurityRbacProperties properties) {
        return new PropertiesPermissionResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RbacEnforcementAspect rbacEnforcementAspect(PermissionResolver permissionResolver,
                                                        GovernanceCoreProperties coreProperties) {
        return new RbacEnforcementAspect(permissionResolver, coreProperties);
    }

    @Bean
    @ConditionalOnAvailableEndpoint
    @ConditionalOnMissingBean
    public SecurityRbacInfoEndpoint securityRbacInfoEndpoint(SecurityRbacProperties properties) {
        return new SecurityRbacInfoEndpoint(properties);
    }
}
