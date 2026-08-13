package com.platform.governance.exception.handler;

import com.platform.governance.core.config.GovernanceCoreProperties;
import com.platform.governance.exception.async.AsyncExceptionLogger;
import com.platform.governance.exception.config.GovernanceExceptionHandlingProperties;
import com.platform.governance.exception.model.ErrorResponse;
import com.platform.governance.exception.model.ExceptionEvent;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;

/**
 * Maps {@code org.springframework.security.access.AccessDeniedException}
 * (thrown by security-rbac's enforcement aspect, or Spring Security's own
 * {@code @PreAuthorize}) to a 403 in the same {@link ErrorResponse} envelope
 * as everything else.
 *
 * Deliberately kept in its OWN class, separate from
 * {@link GlobalExceptionHandler}, and only ever registered when
 * spring-security-core is confirmed on the classpath via
 * {@code @ConditionalOnClass} in GovernanceExceptionHandlingAutoConfiguration
 * - not a try/catch, not a hard import in the main handler class. This is
 * the same isolation lesson this platform learned the hard way with
 * SecurityContextHolder in an earlier module: a class merely REFERENCING an
 * optional type can fail to load - via NoClassDefFoundError at class
 * verification time - even if no code path ever executes that reference,
 * because the JVM has to resolve every type used anywhere in the class to
 * verify it. A service using governance-exception-handling has no
 * obligation to have spring-security-core on its classpath at all, so this
 * class must never be loaded unless that dependency is genuinely present.
 */
@RestControllerAdvice
public class SecurityAccessDeniedExceptionHandler {

    private final GovernanceExceptionHandlingProperties properties;
    private final GovernanceCoreProperties coreProperties;
    private final AsyncExceptionLogger asyncExceptionLogger;

    public SecurityAccessDeniedExceptionHandler(GovernanceExceptionHandlingProperties properties,
                                                 GovernanceCoreProperties coreProperties,
                                                 AsyncExceptionLogger asyncExceptionLogger) {
        this.properties = properties;
        this.coreProperties = coreProperties;
        this.asyncExceptionLogger = asyncExceptionLogger;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String correlationId = MDC.get(coreProperties.getMdcKey());
        String actor = MDC.get(coreProperties.getActorMdcKey());
        Instant now = Instant.now();

        ErrorResponse response = new ErrorResponse(now, correlationId, HttpStatus.FORBIDDEN.value(),
                "ACCESS_DENIED", "You do not have permission to perform this action",
                request.getRequestURI(), List.of(), null);

        asyncExceptionLogger.logAsync(new ExceptionEvent(now, correlationId, actor, ex.getClass().getName(),
                ex.getMessage(), request.getRequestURI(), request.getMethod(),
                HttpStatus.FORBIDDEN.value(), stackTraceOf(ex)));

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    private String stackTraceOf(Throwable ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
