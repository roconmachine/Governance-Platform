package com.roconmachine.governance.core.async;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * The detail that's easy to get wrong when making logging "asynchronous":
 * MDC is {@code ThreadLocal}, so correlation id / actor set on the request
 * thread are invisible on whatever worker thread an executor hands a task
 * to - not because of a bug, but because that's exactly what ThreadLocal
 * means. Without this decorator, log lines produced on the worker thread
 * (or by a log pattern reading {@code %X{correlationId}}) would silently
 * show blank context even though the calling request clearly had one.
 *
 * Spring's {@code ThreadPoolTaskExecutor.setTaskDecorator(...)} is the
 * supported hook for exactly this: it wraps every submitted task, letting
 * us snapshot the MDC context map on the SUBMITTING thread and restore it
 * on the EXECUTING thread before the task runs, then clean up afterward so
 * a reused pool thread doesn't leak one request's context into the next
 * task it happens to run.
 *
 * Shared here in governance-core (rather than duplicated per module) so
 * every module that adds its own async logging executor - governance-audit,
 * governance-exception-handling, and any future one - uses the identical,
 * already-proven propagation mechanism instead of each reinventing it
 * slightly differently.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextAtSubmission = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> contextOnWorkerThread = MDC.getCopyOfContextMap();
            try {
                setOrClear(contextAtSubmission);
                runnable.run();
            } finally {
                setOrClear(contextOnWorkerThread);
            }
        };
    }

    private void setOrClear(Map<String, String> context) {
        if (context != null) {
            MDC.setContextMap(context);
        } else {
            MDC.clear();
        }
    }
}
