package com.roconmachine.securityauth.autoconfigure;

import com.roconmachine.securityauth.core.AuthKeyProvider;
import com.roconmachine.securityauth.core.DefaultKeyProvider;
import com.roconmachine.securityauth.core.JwtService;
import com.roconmachine.securityauth.core.JwtServiceImpl;
import com.roconmachine.securityauth.core.AuthKeyProvider;
import com.roconmachine.securityauth.extension.ClaimsCustomizer;
import com.roconmachine.securityauth.extension.DefaultUserAuthenticationProvider;
import com.roconmachine.securityauth.extension.UserAuthenticationProvider;
import com.roconmachine.securityauth.filter.JwtAuthenticationFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Registers the starter's core beans: {@link AuthKeyProvider}, {@link JwtService},
 * {@link UserAuthenticationProvider}, and {@link JwtAuthenticationFilter}. Every bean
 * is {@code @ConditionalOnMissingBean} so a host application can override any single
 * piece (e.g. supply its own {@code KeyProvider} while keeping the default
 * {@code JwtService}) without disabling the rest of the starter.
 *
 * <p>Discovered via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtSecurityProperties.class)
@ConditionalOnProperty(prefix = "jwt.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JwtSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuthKeyProvider.class)
    public AuthKeyProvider authKeyProvider(JwtSecurityProperties properties) {
        return new DefaultKeyProvider(properties);
    }

    @Bean
    @ConditionalOnMissingBean(JwtService.class)
    public JwtService jwtService(JwtSecurityProperties properties,
                                 AuthKeyProvider keyProvider,
                                  List<ClaimsCustomizer> claimsCustomizers) {
        return new JwtServiceImpl(properties, keyProvider, claimsCustomizers);
    }

    @Bean
    @ConditionalOnMissingBean(UserAuthenticationProvider.class)
    public UserAuthenticationProvider userAuthenticationProvider() {
        return new DefaultUserAuthenticationProvider();
    }

    @Bean
    @ConditionalOnMissingBean(JwtAuthenticationFilter.class)
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService,
                                                             JwtSecurityProperties properties,
                                                             UserAuthenticationProvider userAuthenticationProvider) {
        return new JwtAuthenticationFilter(jwtService, properties, userAuthenticationProvider);
    }
}
