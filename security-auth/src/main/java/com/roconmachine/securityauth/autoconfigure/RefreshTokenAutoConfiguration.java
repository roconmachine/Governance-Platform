package com.roconmachine.securityauth.autoconfigure;

import com.roconmachine.securityauth.core.DefaultRefreshTokenService;
import com.roconmachine.securityauth.core.InMemoryRefreshTokenStore;
import com.roconmachine.securityauth.core.JwtService;
import com.roconmachine.securityauth.core.RefreshTokenService;
import com.roconmachine.securityauth.core.RefreshTokenStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Registers one-time-use refresh token rotation support: {@link RefreshTokenStore}
 * (defaults to the dev-only {@link InMemoryRefreshTokenStore}) and
 * {@link RefreshTokenService}. Runs {@code after} {@link JwtSecurityAutoConfiguration}
 * so {@code JwtService} is already available.
 *
 * <p>Controlled independently via {@code jwt.security.refresh-token-rotation-enabled}
 * (default {@code true}) — set to {@code false} if the host application only wants
 * plain stateless JWT refresh tokens from {@code JwtService.generateRefreshToken}
 * with no server-side rotation tracking.
 */
@AutoConfiguration(after = JwtSecurityAutoConfiguration.class)
@ConditionalOnProperty(prefix = "jwt.security", name = "refresh-token-rotation-enabled", havingValue = "true", matchIfMissing = true)
public class RefreshTokenAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RefreshTokenStore.class)
    public RefreshTokenStore refreshTokenStore() {
        return new InMemoryRefreshTokenStore();
    }

    @Bean
    @ConditionalOnMissingBean(RefreshTokenService.class)
    public RefreshTokenService refreshTokenService(JwtSecurityProperties properties,
                                                     JwtService jwtService,
                                                     RefreshTokenStore refreshTokenStore) {
        return new DefaultRefreshTokenService(properties, jwtService, refreshTokenStore);
    }
}
