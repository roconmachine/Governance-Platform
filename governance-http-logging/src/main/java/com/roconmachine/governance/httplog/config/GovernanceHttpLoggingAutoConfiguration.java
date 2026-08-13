package com.roconmachine.governance.httplog.config;

import com.roconmachine.governance.core.async.MdcTaskDecorator;
import com.roconmachine.governance.core.config.GovernanceCoreAutoConfiguration;
import com.roconmachine.governance.core.config.GovernanceCoreProperties;
import com.roconmachine.governance.core.masking.SensitiveDataMasker;
import com.roconmachine.governance.httplog.endpoint.GovernanceHttpLoggingInfoEndpoint;
import com.roconmachine.governance.httplog.filter.HttpLoggingFilter;
import com.roconmachine.governance.httplog.publisher.HttpAccessLogPublisher;
import com.roconmachine.governance.httplog.publisher.LoggingHttpAccessLogPublisher;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Opt-out by default, same as every other governance module. Runs AFTER
 * governance-core's auto-configuration and at a lower filter precedence than
 * CorrelationIdFilter (order value below), so correlationId/actor are
 * already populated in the MDC by the time this filter captures an event.
 */
@AutoConfiguration
@AutoConfigureAfter(GovernanceCoreAutoConfiguration.class)
@EnableConfigurationProperties(GovernanceHttpLoggingProperties.class)
@ConditionalOnProperty(prefix = "governance.http-logging", name = "enabled", matchIfMissing = true)
public class GovernanceHttpLoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(HttpAccessLogPublisher.class)
    public HttpAccessLogPublisher loggingHttpAccessLogPublisher() {
        return new LoggingHttpAccessLogPublisher();
    }

    /**
     * Named explicitly for the same reason every other executor bean in this
     * platform is: a real application likely already has other Executor
     * beans, so matching by type alone risks ambiguity.
     */
    @Bean("governanceHttpLoggingExecutor")
    @ConditionalOnMissingBean(name = "governanceHttpLoggingExecutor")
    public Executor governanceHttpLoggingExecutor(GovernanceHttpLoggingProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getAsyncPool().getCorePoolSize());
        executor.setMaxPoolSize(properties.getAsyncPool().getMaxPoolSize());
        executor.setQueueCapacity(properties.getAsyncPool().getQueueCapacity());
        executor.setThreadNamePrefix(properties.getAsyncPool().getThreadNamePrefix());
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnClass(Filter.class)
    @ConditionalOnMissingBean(HttpLoggingFilter.class)
    public FilterRegistrationBean<HttpLoggingFilter> httpLoggingFilter(GovernanceHttpLoggingProperties properties,
                                                                        GovernanceCoreProperties coreProperties,
                                                                        SensitiveDataMasker masker,
                                                                        HttpAccessLogPublisher publisher,
                                                                        @Qualifier("governanceHttpLoggingExecutor") Executor executor) {
        FilterRegistrationBean<HttpLoggingFilter> registration = new FilterRegistrationBean<>(
                new HttpLoggingFilter(properties, coreProperties, masker, publisher, executor));
        // One step after security-crypto's EncryptedApiRequestDecryptionFilter
        // (HIGHEST_PRECEDENCE + 2, if present) and security-auth's
        // JwtAuthenticationFilter (HIGHEST_PRECEDENCE + 1), itself one step
        // after governance-core's CorrelationIdFilter (HIGHEST_PRECEDENCE) -
        // so correlationId, actor, and any @EncryptedAPI decryption have all
        // already happened when this filter captures an event, whether or
        // not either of those modules is even on the classpath (this filter
        // doesn't depend on them - just runs after them if they are present).
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
        registration.addUrlPatterns("/*");
        registration.setName("governanceHttpLoggingFilter");
        return registration;
    }

    @Bean
    @ConditionalOnAvailableEndpoint
    @ConditionalOnMissingBean
    public GovernanceHttpLoggingInfoEndpoint governanceHttpLoggingInfoEndpoint(GovernanceHttpLoggingProperties properties) {
        return new GovernanceHttpLoggingInfoEndpoint(properties);
    }
}
