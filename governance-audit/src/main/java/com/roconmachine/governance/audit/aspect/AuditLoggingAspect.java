package com.roconmachine.governance.audit.aspect;

import com.roconmachine.governance.audit.annotation.Auditable;
import com.roconmachine.governance.audit.config.GovernanceAuditProperties;
import com.roconmachine.governance.audit.model.AuditEvent;
import com.roconmachine.governance.audit.publisher.AuditEventPublisher;
import com.roconmachine.governance.core.config.GovernanceCoreProperties;
import com.roconmachine.governance.core.masking.SensitiveDataMasker;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * The actual enforcement of the @Auditable contract: every call to an
 * annotated method - success or failure - produces exactly one AuditEvent.
 * A developer cannot "forget" to audit a governed action; they can only
 * remove the annotation, which is a visible, reviewable code change (and can
 * itself be guarded by the governance-archunit, e.g. "all methods in
 * PaymentService must carry @Auditable").
 *
 * {@code @Auditable(async = true)} hands the actual
 * {@code AuditEventPublisher.publish()} call off to a bounded executor
 * (see GovernanceAuditAutoConfiguration) instead of running it on the
 * calling thread - the annotated method returns without waiting on
 * whatever the publisher does. Same fail-safe shape used throughout this
 * platform (governance-exception-handling's AsyncExceptionLogger): the
 * executor's CallerRunsPolicy means that under sustained saturation the
 * call falls back to synchronous publishing rather than ever silently
 * dropping an audit event - losing visibility into a governed action is
 * worse than a brief latency hit. Console/log output either way goes
 * through the same AuditEventPublisher; async only changes WHICH thread
 * runs that call, never what gets logged.
 */
@Aspect
public class AuditLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLoggingAspect.class);

    private final GovernanceAuditProperties properties;
    private final GovernanceCoreProperties coreProperties;
    private final AuditEventPublisher publisher;
    private final SensitiveDataMasker masker;
    private final Executor asyncExecutor;
    private final String serviceName;

    public AuditLoggingAspect(GovernanceAuditProperties properties,
                               GovernanceCoreProperties coreProperties,
                               AuditEventPublisher publisher,
                               SensitiveDataMasker masker,
                               Executor asyncExecutor,
                               String serviceName) {
        this.properties = properties;
        this.coreProperties = coreProperties;
        this.publisher = publisher;
        this.masker = masker;
        this.asyncExecutor = asyncExecutor;
        this.serviceName = serviceName;
    }

    @Around("@annotation(com.roconmachine.governance.audit.annotation.Auditable)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Auditable auditable = method.getAnnotation(Auditable.class);

        String action = auditable.action().isBlank() ? method.getName() : auditable.action();
        String resource = auditable.resource().isBlank() ? method.getDeclaringClass().getSimpleName() : auditable.resource();
        String correlationId = MDC.get(coreProperties.getMdcKey());
        String actor = MDC.get(coreProperties.getActorMdcKey());
        if (actor == null || actor.isBlank()) {
            actor = "system";
        }

        long start = System.currentTimeMillis();
        Instant timestamp = Instant.now();

        try {
            Object result = joinPoint.proceed();

            String detail = buildDetail(auditable, joinPoint, result, null);
            publish(new AuditEvent(serviceName, correlationId, action, resource, actor, "SUCCESS",
                    detail, System.currentTimeMillis() - start, timestamp), auditable.async());

            return result;
        } catch (Throwable ex) {
            String detail = buildDetail(auditable, joinPoint, null, ex);
            publish(new AuditEvent(serviceName, correlationId, action, resource, actor, "FAILURE",
                    detail, System.currentTimeMillis() - start, timestamp), auditable.async());
            throw ex;
        }
    }

    private String buildDetail(Auditable auditable, ProceedingJoinPoint joinPoint, Object result, Throwable error) {
        StringBuilder sb = new StringBuilder();
        if (auditable.captureArgs() && joinPoint.getArgs() != null && joinPoint.getArgs().length > 0) {
            sb.append("args=[");
            sb.append(extractArguments(joinPoint));
//            for (Object arg : joinPoint.getArgs()) {
//                sb.append(coreProperties.isMaskSensitiveData() ? masker.mask(arg) : String.valueOf(arg)).append("; ");
//            }
            sb.append("]");
        }
        if (auditable.captureResult() && result != null) {
            sb.append(" result=").append(coreProperties.isMaskSensitiveData() ? masker.mask(result) : String.valueOf(result));
        }
        if (error != null) {
            sb.append(" error=").append(error.getClass().getSimpleName()).append(": ").append(error.getMessage());
        }
        return sb.toString();
    }
    private String extractArguments(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        if (parameterNames == null || args == null || parameterNames.length == 0) {
            return "none";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parameterNames.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }

            String name = parameterNames[i];
            Object value = args[i];

            builder.append(name).append("=");

            // Format strings with quotes, handles null gracefully
            if (value instanceof String) {
                builder.append("'").append(value).append("'");
            } else {
                builder.append(Objects.toString(value, "null"));
            }
        }

        return builder.toString();
    }

    private void publish(AuditEvent event, boolean async) {
        // NOTE: when async=true, governance.audit.fail-on-publish-error has no
        // effect on the CALLER - a rethrow from publishSafely() on the async
        // executor thread cannot propagate back to a method that has already
        // returned (or already thrown its own real exception) on the calling
        // thread. fail-on-publish-error only meaningfully fails the caller's
        // own transaction when async=false. This is a real, not cosmetic,
        // trade-off of choosing async - document it for anyone tuning that flag.
        if (!async) {
            publishSafely(event);
            return;
        }
        try {
            asyncExecutor.execute(() -> publishSafely(event));
        } catch (RejectedExecutionException e) {
            // Only reachable if the executor itself is shut down (e.g. during
            // application shutdown) - CallerRunsPolicy handles ordinary
            // saturation before it ever gets here.
            log.warn("Async audit executor rejected task, publishing synchronously instead: {}", event, e);
            publishSafely(event);
        }
    }

    private void publishSafely(AuditEvent event) {
        try {
            publisher.publish(event);
        } catch (Exception publishError) {
            log.error("Failed to publish audit event: {}", event, publishError);
            if (properties.isFailOnPublishError()) {
                throw publishError;
            }
        }
    }
}
