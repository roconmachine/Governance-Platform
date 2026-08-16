package com.roconmachine.security.auth.config;

import com.roconmachine.governance.core.config.GovernanceCoreAutoConfiguration;
import com.roconmachine.governance.core.config.GovernanceCoreProperties;
import com.roconmachine.security.auth.endpoint.SecurityAuthInfoEndpoint;
import com.roconmachine.security.auth.filter.JwtAuthenticationFilter;
import com.roconmachine.security.auth.jwt.JwtTokenValidator;
import com.roconmachine.security.auth.jwt.TokenValidator;
import jakarta.servlet.Filter;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Opt-out by default, same pattern as every other module in this suite.
 *
 * Filter ordering across the platform (lower runs first):
 *   governance-core CorrelationIdFilter        Ordered.HIGHEST_PRECEDENCE     (MIN_VALUE)
 *   security-auth JwtAuthenticationFilter       Ordered.HIGHEST_PRECEDENCE + 1 (this class)
 *   governance-http-logging HttpLoggingFilter   Ordered.HIGHEST_PRECEDENCE + 2
 * so correlation id exists before auth runs, and the resolved actor exists
 * in MDC before HTTP access logging captures it.
 */
@AutoConfiguration
@AutoConfigureAfter(GovernanceCoreAutoConfiguration.class)
@EnableConfigurationProperties(SecurityAuthProperties.class)
@ConditionalOnProperty(prefix = "security.auth", name = "enabled", matchIfMissing = true)
public class SecurityAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TokenValidator tokenValidator(SecurityAuthProperties properties) {
        return new JwtTokenValidator(properties);
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnClass(Filter.class)
    @ConditionalOnMissingBean(JwtAuthenticationFilter.class)
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilter(
            TokenValidator tokenValidator, SecurityAuthProperties properties, GovernanceCoreProperties coreProperties) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(
                new JwtAuthenticationFilter(tokenValidator, properties, coreProperties));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/*");
        registration.setName("securityAuthJwtFilter");
        return registration;
    }

    @Bean
    @ConditionalOnAvailableEndpoint
    @ConditionalOnMissingBean
    public SecurityAuthInfoEndpoint securityAuthInfoEndpoint(SecurityAuthProperties properties) {
        return new SecurityAuthInfoEndpoint(properties);
    }
}
