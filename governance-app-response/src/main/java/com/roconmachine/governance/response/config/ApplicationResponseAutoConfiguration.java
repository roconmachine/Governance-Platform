package com.roconmachine.governance.response.config;

import com.roconmachine.governance.core.config.GovernanceCoreAutoConfiguration;
import com.roconmachine.governance.core.config.GovernanceCoreProperties;
import com.roconmachine.governance.response.endpoint.ApplicationResponseInfoEndpoint;
import com.roconmachine.governance.response.handler.GlobalAppExceptionHandler;
import com.roconmachine.governance.response.handler.SecurityAccessDeniedExceptionHandler;
import com.roconmachine.governance.response.model.AppResponseFactory;
import com.roconmachine.governance.response.trace.MdcTraceIdProvider;
import com.roconmachine.governance.response.trace.MicrometerTraceIdProvider;
import com.roconmachine.governance.response.trace.TraceIdProvider;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Opt-out by default, same pattern as every other module in this suite.
 * {@code applicationResponsePropertiesValidator} is not
 * {@code @ConditionalOnWebApplication} - it validates {@code service-id} in
 * ANY application type (a batch/worker service using only
 * BusinessException/SystemException for its own error handling still gets
 * fail-fast validation); only the web-specific pieces
 * ({@code GlobalAppExceptionHandler}, the actuator endpoint) are gated to
 * web applications.
 */
@AutoConfiguration
@AutoConfigureAfter(GovernanceCoreAutoConfiguration.class)
@EnableConfigurationProperties(ApplicationResponseProperties.class)
@ConditionalOnProperty(prefix = "application.response", name = "enabled", matchIfMissing = true)
public class ApplicationResponseAutoConfiguration {

    /**
     * Deliberately runs validation INSIDE the @Bean method body, not just at
     * some later point - Spring instantiates this bean during context
     * refresh, so a misconfigured/missing service-id fails application
     * startup immediately with a clear diagnostic, rather than failing on
     * the first exception a real request happens to trigger.
     */
    @Bean
    @ConditionalOnMissingBean
    public ApplicationResponsePropertiesValidator applicationResponsePropertiesValidator(
            ApplicationResponseProperties properties) {
        ApplicationResponsePropertiesValidator validator = new ApplicationResponsePropertiesValidator();
        validator.validate(properties);
        return validator;
    }

    /**
     * Registered ONLY when Micrometer Tracing's Tracer type is NOT on the
     * classpath - mutually exclusive with the nested configuration below via
     * @ConditionalOnMissingClass on the exact same class name, the same
     * deterministic (no bean-ordering dependency) pattern established
     * earlier in this platform for isolating optional integrations.
     */
    @Bean
    @ConditionalOnMissingBean(TraceIdProvider.class)
    @ConditionalOnMissingClass("io.micrometer.tracing.Tracer")
    public TraceIdProvider mdcTraceIdProvider(GovernanceCoreProperties coreProperties) {
        return new MdcTraceIdProvider(coreProperties);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
    static class MicrometerTraceIdProviderConfiguration {

        @Bean
        @ConditionalOnMissingBean(TraceIdProvider.class)
        public TraceIdProvider micrometerTraceIdProvider(io.micrometer.tracing.Tracer tracer,
                                                           GovernanceCoreProperties coreProperties) {
            return new MicrometerTraceIdProvider(tracer, coreProperties);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public AppResponseFactory appResponseFactory(ApplicationResponseProperties properties,
                                                  TraceIdProvider traceIdProvider) {
        return new AppResponseFactory(properties, traceIdProvider);
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean
    public GlobalAppExceptionHandler globalAppExceptionHandler(ApplicationResponseProperties properties,
                                                                TraceIdProvider traceIdProvider) {
        return new GlobalAppExceptionHandler(properties, traceIdProvider);
    }

    @Bean
    @ConditionalOnAvailableEndpoint
    @ConditionalOnMissingBean
    public ApplicationResponseInfoEndpoint applicationResponseInfoEndpoint(ApplicationResponseProperties properties) {
        return new ApplicationResponseInfoEndpoint(properties);
    }

    /**
     * Registered ONLY when spring-security-core's AccessDeniedException is
     * confirmed present via bytecode-metadata inspection (ASM), not by
     * loading the class - see SecurityAccessDeniedExceptionHandler's Javadoc
     * for why that distinction matters. A service with no Spring Security
     * dependency at all still gets every other handler in this module
     * working normally; it just never loads this one extra class.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.access.AccessDeniedException")
    static class SecurityExceptionHandlingConfiguration {

        @Bean
        @ConditionalOnWebApplication
        @ConditionalOnMissingBean
        public SecurityAccessDeniedExceptionHandler securityAccessDeniedExceptionHandler(
                ApplicationResponseProperties properties, TraceIdProvider traceIdProvider) {
            return new SecurityAccessDeniedExceptionHandler(properties, traceIdProvider);
        }
    }
}
