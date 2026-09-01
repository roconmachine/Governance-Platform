package com.roconmachine.security.auth.config;

import com.roconmachine.governance.core.config.GovernanceCoreAutoConfiguration;
import com.roconmachine.governance.core.config.GovernanceCoreProperties;
import com.roconmachine.security.auth.endpoint.SecurityAuthInfoEndpoint;
import com.roconmachine.security.auth.filter.JwtAuthenticationFilter;
import com.roconmachine.security.auth.jwt.*;
import com.roconmachine.security.auth.key.PropertiesSigningKeyProvider;
import com.roconmachine.security.auth.key.SigningKeyProvider;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@AutoConfiguration
@AutoConfigureAfter(GovernanceCoreAutoConfiguration.class)
@EnableConfigurationProperties(SecurityAuthProperties.class)
@ConditionalOnProperty(prefix = "security.auth", name = "enabled", matchIfMissing = true)
public class SecurityAuthAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SecurityAuthAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public TokenValidator tokenValidator(SecurityAuthProperties properties) {
        // Use asymmetric validator if public key source is configured
        if (properties.isAsymmetricKeyConfigured()) {
            logger.info("Using AsymmetricJwtValidator for token validation");
            return new AsymmetricJwtValidator(properties);
        }

        // Fall back to HMAC validator
        if (properties.isHmacConfigured()) {
            logger.info("Using JwtTokenValidator (HMAC) for token validation");
            return new JwtTokenValidator(properties);
        }

        throw new IllegalStateException(
                "No token validator configuration found. Configure either:\n" +
                "  - security.auth.hmac-secret (for HMAC/symmetric keys), OR\n" +
                "  - security.auth.jwks-uri (for external JWKS endpoint), OR\n" +
                "  - security.auth.public-key-pem (for static PEM public key)");
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
    @Bean
    @ConditionalOnMissingBean
    public SigningKeyProvider signingKeyProvider(SecurityAuthProperties properties) {
        return new PropertiesSigningKeyProvider(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenIssuer tokenIssuer(SigningKeyProvider signingKeyProvider, SecurityAuthProperties properties) {
        return new JwtTokenIssuer(signingKeyProvider, properties);
    }

    @Bean
    @ConditionalOnAvailableEndpoint
    @ConditionalOnMissingBean
    public SecurityAuthInfoEndpoint securityTokenIssuerInfoEndpoint(SecurityAuthProperties properties) {
        return new SecurityAuthInfoEndpoint(properties);
    }
}
