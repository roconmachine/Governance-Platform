package com.platform.governance.exception.config;

import com.platform.governance.core.config.GovernanceCoreAutoConfiguration;
import com.platform.governance.core.config.GovernanceCoreProperties;
import com.platform.governance.exception.async.AsyncExceptionLogger;
import com.platform.governance.core.async.MdcTaskDecorator;
import com.platform.governance.exception.endpoint.GovernanceExceptionHandlingInfoEndpoint;
import com.platform.governance.exception.handler.GlobalExceptionHandler;
import com.platform.governance.exception.handler.SecurityAccessDeniedExceptionHandler;
import com.platform.governance.exception.publisher.ExceptionEventPublisher;
import com.platform.governance.exception.publisher.LoggingExceptionEventPublisher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Opt-out by default, same pattern as every other module in this suite.
 * Class-level {@code @ConditionalOnWebApplication}: there is nothing for
 * this module to do in a non-web application, since it exists entirely to
 * register {@code @RestControllerAdvice} beans.
 */
@AutoConfiguration
@AutoConfigureAfter(GovernanceCoreAutoConfiguration.class)
@EnableConfigurationProperties(GovernanceExceptionHandlingProperties.class)
@ConditionalOnProperty(prefix = "governance.exception-handling", name = "enabled", matchIfMissing = true)
@ConditionalOnWebApplication
public class GovernanceExceptionHandlingAutoConfiguration {

    /**
     * Named explicitly (rather than relying on type-based @ConditionalOnMissingBean)
     * because a real application is likely to already have OTHER Executor
     * beans (e.g. Spring's own "applicationTaskExecutor") - matching by type
     * alone risks either skipping our bean unnecessarily or, worse, colliding
     * at injection time elsewhere. The bean name is also how
     * asyncExceptionLogger() below unambiguously wires this specific executor.
     */
    @Bean("governanceExceptionExecutor")
    @ConditionalOnMissingBean(name = "governanceExceptionExecutor")
    public Executor governanceExceptionExecutor(GovernanceExceptionHandlingProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getAsyncPool().getCorePoolSize());
        executor.setMaxPoolSize(properties.getAsyncPool().getMaxPoolSize());
        executor.setQueueCapacity(properties.getAsyncPool().getQueueCapacity());
        executor.setThreadNamePrefix(properties.getAsyncPool().getThreadNamePrefix());
        executor.setTaskDecorator(new MdcTaskDecorator());
        // Never drop a log task under saturation - run it on the calling thread
        // instead. See GovernanceExceptionHandlingProperties.Async's Javadoc.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean(ExceptionEventPublisher.class)
    public ExceptionEventPublisher exceptionEventPublisher() {
        return new LoggingExceptionEventPublisher();
    }

    @Bean
    @ConditionalOnMissingBean
    public AsyncExceptionLogger asyncExceptionLogger(@Qualifier("governanceExceptionExecutor") Executor executor,
                                                       ExceptionEventPublisher publisher,
                                                       GovernanceExceptionHandlingProperties properties) {
        return new AsyncExceptionLogger(executor, publisher, properties.isAsync());
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler(GovernanceExceptionHandlingProperties properties,
                                                          GovernanceCoreProperties coreProperties,
                                                          AsyncExceptionLogger asyncExceptionLogger) {
        return new GlobalExceptionHandler(properties, coreProperties, asyncExceptionLogger);
    }

    @Bean
    @ConditionalOnAvailableEndpoint
    @ConditionalOnMissingBean
    public GovernanceExceptionHandlingInfoEndpoint governanceExceptionHandlingInfoEndpoint(
            GovernanceExceptionHandlingProperties properties) {
        return new GovernanceExceptionHandlingInfoEndpoint(properties);
    }

    /**
     * Registered ONLY when spring-security-core's AccessDeniedException is
     * confirmed present via bytecode-metadata inspection (ASM), not by
     * loading the class - see SecurityAccessDeniedExceptionHandler's Javadoc
     * for why that distinction matters.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.access.AccessDeniedException")
    static class SecurityExceptionHandlingConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public SecurityAccessDeniedExceptionHandler securityAccessDeniedExceptionHandler(
                GovernanceExceptionHandlingProperties properties,
                GovernanceCoreProperties coreProperties,
                AsyncExceptionLogger asyncExceptionLogger) {
            return new SecurityAccessDeniedExceptionHandler(properties, coreProperties, asyncExceptionLogger);
        }
    }
}
