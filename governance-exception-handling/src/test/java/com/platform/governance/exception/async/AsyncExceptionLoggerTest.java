package com.platform.governance.exception.async;

import com.platform.governance.core.async.MdcTaskDecorator;
import com.platform.governance.exception.model.ExceptionEvent;
import com.platform.governance.exception.publisher.ExceptionEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncExceptionLoggerTest {

    private ThreadPoolTaskExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
        MDC.clear();
    }

    private ThreadPoolTaskExecutor buildExecutor(int coreSize, int maxSize, int queueCapacity) {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(coreSize);
        exec.setMaxPoolSize(maxSize);
        exec.setQueueCapacity(queueCapacity);
        exec.setThreadNamePrefix("test-async-exception-");
        exec.setTaskDecorator(new MdcTaskDecorator());
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.initialize();
        return exec;
    }

    private ExceptionEvent sampleEvent() {
        return new ExceptionEvent(Instant.now(), "corr-1", "actor-1",
                RuntimeException.class.getName(), "boom", "/x", "GET", 500, "stack-trace-goes-here");
    }

    @Test
    void publishingHappensOffTheCallingThread() throws InterruptedException {
        executor = buildExecutor(1, 1, 10);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> workerThreadName = new AtomicReference<>();

        AsyncExceptionLogger logger = new AsyncExceptionLogger(executor, event -> {
            workerThreadName.set(Thread.currentThread().getName());
            latch.countDown();
        }, true);

        String callingThreadName = Thread.currentThread().getName();
        logger.logAsync(sampleEvent());

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(workerThreadName.get()).isNotEqualTo(callingThreadName);
        assertThat(workerThreadName.get()).startsWith("test-async-exception-");
    }

    @Test
    void mdcContextFromTheSubmittingThreadIsVisibleOnTheWorkerThread() throws InterruptedException {
        executor = buildExecutor(1, 1, 10);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> capturedCorrelationId = new AtomicReference<>();

        AsyncExceptionLogger logger = new AsyncExceptionLogger(executor, event -> {
            capturedCorrelationId.set(MDC.get("correlationId"));
            latch.countDown();
        }, true);

        MDC.put("correlationId", "abc-123");
        try {
            logger.logAsync(sampleEvent());
        } finally {
            MDC.remove("correlationId"); // simulate the request thread's filter cleanup happening immediately after
        }

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        // proves propagation actually happened - if MdcTaskDecorator did nothing,
        // this would be null since MDC is ThreadLocal and the submitting thread
        // already cleared its own copy before the worker thread ran.
        assertThat(capturedCorrelationId.get()).isEqualTo("abc-123");
    }

    @Test
    void neverDropsEventsUnderSustainedSaturation() throws InterruptedException {
        // Deliberately tiny pool + tiny queue with a slow publisher, so
        // saturation is guaranteed - this exercises CallerRunsPolicy.
        executor = buildExecutor(1, 1, 1);
        int totalEvents = 20;
        AtomicInteger publishedCount = new AtomicInteger();
        CountDownLatch allDone = new CountDownLatch(totalEvents);

        ExceptionEventPublisher slowPublisher = event -> {
            try {
                Thread.sleep(15);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            publishedCount.incrementAndGet();
            allDone.countDown();
        };
        AsyncExceptionLogger logger = new AsyncExceptionLogger(executor, slowPublisher, true);

        for (int i = 0; i < totalEvents; i++) {
            logger.logAsync(sampleEvent());
        }

        assertThat(allDone.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(publishedCount.get()).isEqualTo(totalEvents); // every one published - none silently dropped
    }

    @Test
    void asyncFalseForcesSynchronousPublishingOnTheCallingThread() {
        executor = buildExecutor(1, 1, 10);
        AtomicReference<String> publishingThreadName = new AtomicReference<>();

        AsyncExceptionLogger logger = new AsyncExceptionLogger(executor, event ->
                publishingThreadName.set(Thread.currentThread().getName()), false);

        String callingThreadName = Thread.currentThread().getName();
        logger.logAsync(sampleEvent());

        // no latch/wait needed - if this weren't synchronous, the assertion
        // below could race ahead of the publish call completing
        assertThat(publishingThreadName.get()).isEqualTo(callingThreadName);
    }
}
