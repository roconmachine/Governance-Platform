package com.roconmachine.governance.idempotency.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roconmachine.governance.idempotency.IdempotencyFilter;
import com.roconmachine.governance.idempotency.IdempotencyStore;
import com.roconmachine.governance.idempotency.impl.InMemoryIdempotencyStore;
import com.roconmachine.governance.idempotency.impl.RedisIdempotencyStore;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@AutoConfiguration
public class GovernanceAutoConfiguration {

    // --- Redis Strategy Auto-Configuration ---
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    static class RedisIdempotencyConfiguration {

        @Bean
        @ConditionalOnMissingBean(IdempotencyStore.class)
        public IdempotencyStore redisIdempotencyStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
            return new RedisIdempotencyStore(redisTemplate, objectMapper);
        }
    }

    // --- In-Memory Fallback Auto-Configuration (For Local Dev / Testing) ---
    @Configuration(proxyBeanMethods = false)
    static class InMemoryIdempotencyConfiguration {

        @Bean
        @ConditionalOnMissingBean(IdempotencyStore.class)
        public IdempotencyStore inMemoryIdempotencyStore() {
            return new InMemoryIdempotencyStore();
        }
    }

    // --- Web Filter Registration ---
    @Bean
    @ConditionalOnBean(IdempotencyStore.class)
    @ConditionalOnMissingBean
    public FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration(
            IdempotencyStore idempotencyStore,
            RequestMappingHandlerMapping handlerMapping) {

        FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new IdempotencyFilter(idempotencyStore, handlerMapping));
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        return registration;
    }
}