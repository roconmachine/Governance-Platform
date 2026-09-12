package com.roconmachine.securityauth.autoconfigure;

import com.roconmachine.securityauth.filter.JwtAuthenticationFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Registers a stateless, JWT-protected default {@link SecurityFilterChain} so a host
 * application gets a working, secure baseline with zero security config of its own.
 *
 * <p>Runs {@code after} {@link JwtSecurityAutoConfiguration} so {@code JwtAuthenticationFilter}
 * is already in the context. Backs off entirely if the host defines its own
 * {@code SecurityFilterChain} bean ({@code @ConditionalOnMissingBean}) or sets
 * {@code jwt.security.auto-security-chain-enabled=false} — in that case, host apps
 * should inject {@code JwtAuthenticationFilter} into their own chain with
 * {@code .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)}.
 */
@AutoConfiguration(after = JwtSecurityAutoConfiguration.class)
@ConditionalOnClass(SecurityFilterChain.class)
@ConditionalOnProperty(prefix = "jwt.security", name = "auto-security-chain-enabled", havingValue = "true", matchIfMissing = true)
public class JwtSecurityFilterChainAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain jwtDefaultSecurityFilterChain(HttpSecurity http,
                                                               JwtAuthenticationFilter jwtAuthenticationFilter,
                                                               JwtSecurityProperties properties) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    for (String pattern : properties.getPermitAllPatterns()) {
                        auth.requestMatchers(pattern).permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
