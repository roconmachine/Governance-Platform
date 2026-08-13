package com.platform.governance.audit.config;

import com.platform.governance.audit.aspect.AuditLoggingAspect;
import com.platform.governance.audit.endpoint.GovernanceAuditInfoEndpoint;
import com.platform.governance.audit.publisher.AuditEventPublisher;
import com.platform.governance.audit.publisher.LoggingAuditEventPublisher;
import com.platform.governance.audit.publisher.NoopAuditEventPublisher;
import com.platform.governance.core.async.MdcTaskDecorator;
import com.platform.governance.core.config.GovernanceCoreAutoConfiguration;
import com.platform.governance.core.config.GovernanceCoreProperties;
import com.platform.governance.core.masking.SensitiveDataMasker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Entry point Spring Boot loads via META-INF/spring/...AutoConfiguration.imports.
 *
 * Everything here is opt-out (enabled by default, @ConditionalOnProperty with
 * matchIfMissing = true) because the whole point of "governance through
 * code" is that a service gets the policy for free just by depending on this
 * jar - it should have to actively opt OUT, not opt in.
 *
 * Correlation id / actor propagation and @Sensitive masking are NOT defined
 * here anymore - they come from governance-core, which this module depends
 * on, so audit and http-logging (and any future module added later) share the exact
 * same masking policy and request identity instead of each redefining it.
 *
 * Every bean is also @ConditionalOnMissingBean so an individual service can
 * still override a piece (e.g. plug in a Kafka-backed AuditEventPublisher)
 * without forking the module.
 */
@AutoConfiguration
@AutoConfigureAfter(GovernanceCoreAutoConfiguration.class)
@EnableConfigurationProperties(GovernanceAuditProperties.class)
@ConditionalOnProperty(prefix = "governance.audit", name = "enabled", matchIfMissing = true)
public class GovernanceAuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuditEventPublisher.class)
    @ConditionalOnProperty(prefix = "governance.audit", name = "sink", havingValue = "LOG", matchIfMissing = true)
    public AuditEventPublisher loggingAuditEventPublisher() {
        return new LoggingAuditEventPublisher();
    }

    @Bean
    @ConditionalOnMissingBean(AuditEventPublisher.class)
    @ConditionalOnProperty(prefix = "governance.audit", name = "sink", havingValue = "NOOP")
    public AuditEventPublisher noopAuditEventPublisher() {
        return new NoopAuditEventPublisher();
    }

    /**
     * Named explicitly (rather than relying on type-based @ConditionalOnMissingBean)
     * for the same reason governance-exception-handling's executor is: a real
     * application is likely to already have OTHER Executor beans (e.g.
     * Spring's own "applicationTaskExecutor"), so matching by type alone
     * risks either skipping this bean unnecessarily or colliding at
     * injection time elsewhere.
     */
    @Bean("governanceAuditExecutor")
    @ConditionalOnMissingBean(name = "governanceAuditExecutor")
    public Executor governanceAuditExecutor(GovernanceAuditProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getAsync().getCorePoolSize());
        executor.setMaxPoolSize(properties.getAsync().getMaxPoolSize());
        executor.setQueueCapacity(properties.getAsync().getQueueCapacity());
        executor.setThreadNamePrefix(properties.getAsync().getThreadNamePrefix());
        executor.setTaskDecorator(new MdcTaskDecorator());
        // Never drop an audit event under saturation - run it on the calling
        // thread instead. Same fail-safe shape as governance-exception-handling.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLoggingAspect auditLoggingAspect(GovernanceAuditProperties properties,
                                                  GovernanceCoreProperties coreProperties,
                                                  AuditEventPublisher publisher,
                                                  SensitiveDataMasker masker,
                                                  @Qualifier("governanceAuditExecutor") Executor executor,
                                                  Environment environment) {
        String serviceName = resolveServiceName(properties, environment);
        return new AuditLoggingAspect(properties, coreProperties, publisher, masker, executor, serviceName);
    }

    @Bean
    @ConditionalOnAvailableEndpoint
    @ConditionalOnMissingBean
    public GovernanceAuditInfoEndpoint governanceAuditInfoEndpoint(GovernanceAuditProperties properties) {
        return new GovernanceAuditInfoEndpoint(properties);
    }

    /**
     * governance.audit.service-name wins if explicitly set; otherwise falls
     * back to spring.application.name; otherwise "unknown-service" rather
     * than null, so AuditEvent.serviceName is never blank in a log line.
     */
    private String resolveServiceName(GovernanceAuditProperties properties, Environment environment) {
        if (properties.getServiceName() != null && !properties.getServiceName().isBlank()) {
            return properties.getServiceName();
        }
        return environment.getProperty("spring.application.name", "unknown-service");
    }
}
