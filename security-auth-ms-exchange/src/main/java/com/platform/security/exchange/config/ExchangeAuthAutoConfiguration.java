package com.platform.security.exchange.config;

import com.microsoft.aad.msal4j.IConfidentialClientApplication;
import com.platform.governance.core.config.GovernanceCoreAutoConfiguration;
import com.platform.governance.core.config.GovernanceCoreProperties;
import com.platform.security.exchange.cache.TokenCacheManager;
import com.platform.security.exchange.client.ConfidentialClientFactory;
import com.platform.security.exchange.endpoint.ExchangeAuthInfoEndpoint;
import com.platform.security.exchange.service.DefaultExchangeAuthProvider;
import com.platform.security.exchange.service.ExchangeAuthProvider;
import com.platform.security.exchange.validation.EntraTokenValidator;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Opt-out by default, same pattern as every other module in this suite:
 * depend on the jar, set the required properties (tenant-id, client-id, and
 * one of client-secret/certificate.path), and {@link ExchangeAuthProvider}
 * is available to autowire with no further wiring.
 *
 * Every bean is {@code @ConditionalOnMissingBean} so a service can override
 * any single piece - most commonly {@link EntraTokenValidator} (e.g. to add
 * organization-specific claims checks) or {@link TokenCacheManager} (e.g. to
 * back it with a distributed cache instead of in-memory Caffeine, for a
 * multi-instance deployment that wants to share acquired tokens) - without
 * forking the module.
 */
@AutoConfiguration
@AutoConfigureAfter(GovernanceCoreAutoConfiguration.class)
@EnableConfigurationProperties(ExchangeAuthProperties.class)
@ConditionalOnProperty(prefix = "exchange.auth", name = "enabled", matchIfMissing = true)
public class ExchangeAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ConfidentialClientFactory confidentialClientFactory(ExchangeAuthProperties properties) {
        return new ConfidentialClientFactory(properties);
    }

    /**
     * Built once and shared as a singleton - MSAL4J documents
     * IConfidentialClientApplication as thread-safe and intended to be
     * long-lived, not constructed per request.
     */
    @Bean
    @ConditionalOnMissingBean
    public IConfidentialClientApplication confidentialClientApplication(ConfidentialClientFactory factory) {
        return factory.create();
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenCacheManager tokenCacheManager(ExchangeAuthProperties properties) {
        return new TokenCacheManager(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public EntraTokenValidator entraTokenValidator(ExchangeAuthProperties properties) {
        return new EntraTokenValidator(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExchangeAuthProvider exchangeAuthProvider(IConfidentialClientApplication clientApplication,
                                                      ExchangeAuthProperties properties,
                                                      GovernanceCoreProperties coreProperties,
                                                      TokenCacheManager tokenCacheManager,
                                                      EntraTokenValidator tokenValidator) {
        return new DefaultExchangeAuthProvider(clientApplication, properties, coreProperties,
                tokenCacheManager, tokenValidator);
    }

    @Bean
    @ConditionalOnAvailableEndpoint
    @ConditionalOnMissingBean
    public ExchangeAuthInfoEndpoint exchangeAuthInfoEndpoint(ExchangeAuthProperties properties) {
        return new ExchangeAuthInfoEndpoint(properties);
    }
}
