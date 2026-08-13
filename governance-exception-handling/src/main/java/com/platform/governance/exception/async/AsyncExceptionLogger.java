package com.platform.governance.exception.async;

import com.platform.governance.exception.model.ExceptionEvent;
import com.platform.governance.exception.publisher.ExceptionEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Hands the actual publish() call off to a bounded executor so a slow log
 * sink (network I/O, a remote aggregator, disk contention) never adds
 * latency to the error response the caller is waiting on - when async is
 * enabled (the default, matching this module's original always-async
 * design). Set governance.exception-handling.async=false to force
 * synchronous publishing instead: the event is guaranteed published before
 * the error response is returned, at the cost of adding the publish call to
 * the response latency.
 *
 * The executor is configured (see GovernanceExceptionHandlingAutoConfiguration)
 * with a CallerRunsPolicy rejection handler - under sustained overload, this
 * class's own submission would throw RejectedExecutionException, but that
 * policy makes the executor run the task on the calling thread instead of
 * rejecting outright, so the only failure mode under load is "this one log
 * call became synchronous," never "this exception was never logged at all."
 * The catch block below is a last-resort safety net for the (much rarer)
 * case of the executor being shut down entirely.
 */
public class AsyncExceptionLogger {

    private static final Logger log = LoggerFactory.getLogger(AsyncExceptionLogger.class);

    private final Executor executor;
    private final ExceptionEventPublisher publisher;
    private final boolean async;

    public AsyncExceptionLogger(Executor executor, ExceptionEventPublisher publisher, boolean async) {
        this.executor = executor;
        this.publisher = publisher;
        this.async = async;
    }

    /**
     * Method name kept as {@code logAsync} for API stability within this
     * module even though behavior is now conditional on the {@code async}
     * flag - it still describes the DEFAULT and originally-only behavior.
     */
    public void logAsync(ExceptionEvent event) {
        if (!async) {
            publisher.publish(event);
            return;
        }
        try {
            executor.execute(() -> publisher.publish(event));
        } catch (RejectedExecutionException e) {
            // Only reachable if the executor itself is shut down (e.g. during
            // application shutdown) - CallerRunsPolicy handles ordinary
            // saturation before it ever gets here. Log synchronously right
            // now rather than losing the exception record entirely.
            log.warn("Async exception logging executor rejected task, logging synchronously instead", e);
            publisher.publish(event);
        }
    }
}
