package com.platform.governance.response.handler;

import com.platform.governance.response.config.ApplicationResponseProperties;
import com.platform.governance.response.exception.ExceptionType;
import com.platform.governance.response.model.AppCodeFormatter;
import com.platform.governance.response.model.AppResponse;
import com.platform.governance.response.trace.TraceIdProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps {@code org.springframework.security.access.AccessDeniedException}
 * (thrown by security-rbac's enforcement aspect, or Spring Security's own
 * {@code @PreAuthorize}) to a 403 in the same {@link AppResponse} envelope
 * as everything else, formatted as {@code ServiceID-B-9403} (a fixed
 * reserved event code for access-denied, since this isn't a code any
 * individual service throws deliberately via {@code ErrorCode}).
 *
 * Deliberately kept in its OWN class, separate from
 * {@link GlobalAppExceptionHandler}, and only ever registered when
 * spring-security-core is confirmed on the classpath via
 * {@code @ConditionalOnClass} in {@code ApplicationResponseAutoConfiguration}
 * - the same isolation lesson this platform applies everywhere an
 * integration is genuinely optional: a service with no Spring Security
 * dependency at all must never have this class loaded, since the JVM has
 * to resolve every type referenced in a class (including
 * {@code AccessDeniedException}) to verify it, even for a class it never
 * calls a method on.
 *
 * Without this handler, an {@code AccessDeniedException} would fall
 * through to {@code GlobalAppExceptionHandler}'s generic
 * {@code Exception.class} catch-all and incorrectly return 500 instead of
 * 403 - this class exists specifically to close that gap.
 */
@RestControllerAdvice
public class SecurityAccessDeniedExceptionHandler {

    private static final String ACCESS_DENIED_EVENT_CODE = "9403";

    private final ApplicationResponseProperties properties;
    private final TraceIdProvider traceIdProvider;

    public SecurityAccessDeniedExceptionHandler(ApplicationResponseProperties properties,
                                                 TraceIdProvider traceIdProvider) {
        this.properties = properties;
        this.traceIdProvider = traceIdProvider;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<AppResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        String appCode = AppCodeFormatter.format(properties.getServiceId(), ExceptionType.BUSINESS.getCode(),
                ACCESS_DENIED_EVENT_CODE);
        AppResponse<Object> body = AppResponse.error(HttpStatus.FORBIDDEN.value(), appCode,
                "You do not have permission to perform this action", null, traceIdProvider.currentTraceId());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }
}
