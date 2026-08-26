package com.roconmachine.security.crypto.config;

import com.roconmachine.security.crypto.endpoint.SecurityCryptoInfoEndpoint;
import com.roconmachine.security.crypto.engine.AesGcmEncryptionService;
import com.roconmachine.security.crypto.engine.EncryptionService;
import com.roconmachine.security.crypto.engine.EnvKeyProvider;
import com.roconmachine.security.crypto.engine.KeyProvider;
import com.roconmachine.security.crypto.web.EncryptedApiRequestDecryptionFilter;
import com.roconmachine.security.crypto.web.EncryptedApiResponseBodyAdvice;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Opt-out by default, same pattern as every other module in this suite.
 * Also initializes EncryptedStringConverter's static holder - see that
 * class's Javadoc for why a static bridge is necessary for JPA converters.
 *
 * The @EncryptedAPI feature (EncryptedApiRequestDecryptionFilter,
 * EncryptedApiResponseBodyAdvice) lives in its own nested configuration,
 * gated by @ConditionalOnWebApplication + @ConditionalOnClass on a Spring
 * MVC marker type - the same isolation pattern used throughout this
 * platform for any integration that isn't universally required. A service
 * using only EncryptionService/KeyProvider/the JPA converter (e.g. a batch
 * job with no web layer at all) never loads any class in that nested
 * configuration.
 */
@AutoConfiguration
@EnableConfigurationProperties(SecurityCryptoProperties.class)
@ConditionalOnProperty(prefix = "security.crypto", name = "enabled", matchIfMissing = true)
public class SecurityCryptoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KeyProvider keyProvider(SecurityCryptoProperties properties) {
        return new EnvKeyProvider(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public EncryptionService encryptionService(KeyProvider keyProvider, SecurityCryptoProperties properties) {
        EncryptionService service = new AesGcmEncryptionService(keyProvider, properties);
        EncryptedStringConverter.initialize(service);
        return service;
    }

    @Bean
    @ConditionalOnAvailableEndpoint
    @ConditionalOnMissingBean
    public SecurityCryptoInfoEndpoint securityCryptoInfoEndpoint(SecurityCryptoProperties properties) {
        return new SecurityCryptoInfoEndpoint(properties);
    }

    /**
     * Registered ONLY when this is a web application AND Spring MVC's
     * RequestMappingHandlerMapping type is confirmed present via bytecode
     * metadata inspection - not by loading the class - so a non-web service
     * depending on this module (e.g. for EncryptionService alone) never
     * loads anything in this nested configuration at all.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication
    @ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping")
    @ConditionalOnProperty(prefix = "security.crypto.encrypted-api", name = "enabled", matchIfMissing = true)
    static class EncryptedApiWebConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public FilterRegistrationBean<EncryptedApiRequestDecryptionFilter> encryptedApiRequestDecryptionFilter(
                // Qualified explicitly: Spring Boot Actuator's own
                // ControllerEndpointHandlerMapping (registered whenever any
                // @ControllerEndpoint/@RestControllerEndpoint is active, which
                // is routine on this platform given how many modules expose
                // custom actuator endpoints) is ITSELF a subtype of
                // RequestMappingHandlerMapping - so injecting by type alone is
                // ambiguous whenever actuator web endpoints are present, not
                // just in some edge case. "requestMappingHandlerMapping" is
                // the bean name Spring Boot's own WebMvcAutoConfiguration
                // registers for the standard MVC one this filter actually needs.
                @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
                EncryptionService encryptionService,
                SecurityCryptoProperties properties,
                ObjectMapper objectMapper) {
            FilterRegistrationBean<EncryptedApiRequestDecryptionFilter> registration = new FilterRegistrationBean<>(
                    new EncryptedApiRequestDecryptionFilter(handlerMapping, encryptionService,
                            properties, objectMapper));
            // Runs after correlation-id (HIGHEST_PRECEDENCE) and auth
            // (HIGHEST_PRECEDENCE + 1), before governance-http-logging
            // (HIGHEST_PRECEDENCE + 3) - so HTTP access logs (if body logging
            // is ever enabled) capture the DECRYPTED plaintext, which is more
            // useful operationally than the raw ciphertext wire format.
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
            registration.addUrlPatterns("/*");
            registration.setName("encryptedApiRequestDecryptionFilter");
            return registration;
        }

        @Bean
        @ConditionalOnMissingBean
        public EncryptedApiResponseBodyAdvice encryptedApiResponseBodyAdvice(
                EncryptionService encryptionService, SecurityCryptoProperties properties, ObjectMapper objectMapper) {
            return new EncryptedApiResponseBodyAdvice(encryptionService, properties, objectMapper);
        }
    }
}
