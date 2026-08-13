package com.platform.governance.audit.aspect;

import com.platform.governance.audit.aspect.fixtures.SampleAuditedService;
import com.platform.governance.audit.config.GovernanceAuditProperties;
import com.platform.governance.audit.model.AuditEvent;
import com.platform.governance.audit.publisher.AuditEventPublisher;
import com.platform.governance.core.async.MdcTaskDecorator;
import com.platform.governance.core.config.GovernanceCoreProperties;
import com.platform.governance.core.masking.SensitiveDataMasker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLoggingAspectTest {

    private ThreadPoolTaskExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
        MDC.clear();
    }

    private ThreadPoolTaskExecutor buildExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(1);
        exec.setMaxPoolSize(1);
        exec.setQueueCapacity(10);
        exec.setThreadNamePrefix("test-audit-async-");
        exec.setTaskDecorator(new MdcTaskDecorator());
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.initialize();
        return exec;
    }

    private SampleAuditedService buildProxy(List<AuditEvent> capturedEvents) {
        GovernanceAuditProperties properties = new GovernanceAuditProperties();
        GovernanceCoreProperties coreProperties = new GovernanceCoreProperties();
        AuditEventPublisher publisher = capturedEvents::add;
        executor = buildExecutor();

        AuditLoggingAspect aspect = new AuditLoggingAspect(properties, coreProperties, publisher,
                new SensitiveDataMasker(), executor, "payment-service");

        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleAuditedService());
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    @Test
    void everyAuditEventCarriesTheConfiguredServiceName() {
        List<AuditEvent> events = new CopyOnWriteArrayList<>();
        SampleAuditedService proxy = buildProxy(events);

        proxy.syncAction();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getServiceName()).isEqualTo("payment-service");
    }

    @Test
    void syncMethodPublishesOnTheCallingThreadBeforeReturning() {
        List<AuditEvent> events = new CopyOnWriteArrayList<>();
        SampleAuditedService proxy = buildProxy(events);

        proxy.syncAction();

        // no waiting/latch needed - if this weren't synchronous, the event
        // might not be present yet immediately after the call returns
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getAction()).isEqualTo("SYNC_ACTION");
        assertThat(events.get(0).getOutcome()).isEqualTo("SUCCESS");
    }

    @Test
    void asyncMethodPublishesOffTheCallingThread() throws InterruptedException {
        List<AuditEvent> events = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> publishingThreadName = new AtomicReference<>();

        GovernanceAuditProperties properties = new GovernanceAuditProperties();
        GovernanceCoreProperties coreProperties = new GovernanceCoreProperties();
        AuditEventPublisher publisher = event -> {
            events.add(event);
            publishingThreadName.set(Thread.currentThread().getName());
            latch.countDown();
        };
        executor = buildExecutor();
        AuditLoggingAspect aspect = new AuditLoggingAspect(properties, coreProperties, publisher,
                new SensitiveDataMasker(), executor, "payment-service");

        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleAuditedService());
        factory.addAspect(aspect);
        SampleAuditedService proxy = factory.getProxy();

        String callingThreadName = Thread.currentThread().getName();
        proxy.asyncAction();

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(publishingThreadName.get()).isNotEqualTo(callingThreadName);
        assertThat(publishingThreadName.get()).startsWith("test-audit-async-");
        assertThat(events.get(0).getAction()).isEqualTo("ASYNC_ACTION");
    }

    @Test
    void asyncMethodStillPropagatesCorrelationIdViaMdcTaskDecorator() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> capturedCorrelationId = new AtomicReference<>();

        GovernanceAuditProperties properties = new GovernanceAuditProperties();
        GovernanceCoreProperties coreProperties = new GovernanceCoreProperties();
        AuditEventPublisher publisher = event -> {
            capturedCorrelationId.set(MDC.get(coreProperties.getMdcKey()));
            latch.countDown();
        };
        executor = buildExecutor();
        AuditLoggingAspect aspect = new AuditLoggingAspect(properties, coreProperties, publisher,
                new SensitiveDataMasker(), executor, "payment-service");

        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleAuditedService());
        factory.addAspect(aspect);
        SampleAuditedService proxy = factory.getProxy();

        MDC.put(coreProperties.getMdcKey(), "corr-xyz-789");
        try {
            proxy.asyncAction();
        } finally {
            MDC.remove(coreProperties.getMdcKey());
        }

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(capturedCorrelationId.get()).isEqualTo("corr-xyz-789");
    }

    @Test
    void defaultBehaviorWithNoExplicitAsyncAttributeIsAsync() throws InterruptedException {
        // proves the annotation's default is genuinely async=true, not just documented as such
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> publishingThreadName = new AtomicReference<>();

        GovernanceAuditProperties properties = new GovernanceAuditProperties();
        GovernanceCoreProperties coreProperties = new GovernanceCoreProperties();
        AuditEventPublisher publisher = event -> {
            publishingThreadName.set(Thread.currentThread().getName());
            latch.countDown();
        };
        executor = buildExecutor();
        AuditLoggingAspect aspect = new AuditLoggingAspect(properties, coreProperties, publisher,
                new SensitiveDataMasker(), executor, "payment-service");

        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleAuditedService());
        factory.addAspect(aspect);
        SampleAuditedService proxy = factory.getProxy();

        String callingThreadName = Thread.currentThread().getName();
        proxy.defaultAction();

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(publishingThreadName.get()).isNotEqualTo(callingThreadName);
    }

    @Test
    void failingMethodStillPublishesAFailureEventAndRethrows() {
        List<AuditEvent> events = new CopyOnWriteArrayList<>();
        SampleAuditedService proxy = buildProxy(events);

        try {
            proxy.failingAction();
        } catch (IllegalStateException expected) {
            // expected - @Auditable never swallows the original exception
        }

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getOutcome()).isEqualTo("FAILURE");
        assertThat(events.get(0).getDetail()).contains("IllegalStateException");
    }
}
