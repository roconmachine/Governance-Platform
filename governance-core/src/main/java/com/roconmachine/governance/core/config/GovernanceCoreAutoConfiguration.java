package com.roconmachine.governance.core.config;

import com.roconmachine.governance.core.correlation.CorrelationIdFilter;
import com.roconmachine.governance.core.endpoint.GovernanceInfoEndpoint;
import com.roconmachine.governance.core.masking.SensitiveDataMasker;
import jakarta.servlet.Filter;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Registers the two things every governance module depends on:
 * SensitiveDataMasker and the correlation-id/actor filter. Both audit and
 * http-logging both declare a dependency on governance-core and pick
 * these beans up automatically - no wiring required in either module.
 */
@AutoConfiguration
@EnableConfigurationProperties(GovernanceCoreProperties.class)
@ConditionalOnProperty(prefix = "governance.core", name = "enabled", matchIfMissing = true)
public class GovernanceCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SensitiveDataMasker sensitiveDataMasker() {
        return new SensitiveDataMasker();
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnClass(Filter.class)
    @ConditionalOnMissingBean(CorrelationIdFilter.class)
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter(GovernanceCoreProperties properties) {
        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(new CorrelationIdFilter(properties));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        registration.setName("governanceCorrelationIdFilter");
        return registration;
    }

    @Bean
    @ConditionalOnAvailableEndpoint
    @ConditionalOnMissingBean
    public GovernanceInfoEndpoint governanceInfoEndpoint(GovernanceCoreProperties properties) {
        return new GovernanceInfoEndpoint(properties);
    }
}
