package com.roconmachine.governance.httplog.filter;

import com.roconmachine.governance.core.config.GovernanceCoreProperties;
import com.roconmachine.governance.core.masking.SensitiveDataMasker;
import com.roconmachine.governance.httplog.config.GovernanceHttpLoggingProperties;
import com.roconmachine.governance.httplog.model.HttpAccessLogEvent;
import com.roconmachine.governance.httplog.publisher.HttpAccessLogPublisher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Captures one HttpAccessLogEvent per request, using Spring's
 * ContentCachingRequestWrapper/ContentCachingResponseWrapper so the body can
 * be read for logging *and* still delivered to the actual controller/servlet
 * (a plain HttpServletRequest's input stream can only be read once - these
 * wrappers buffer it so both the filter and the app see the full content).
 *
 * Registered with a lower precedence than governance-core's
 * CorrelationIdFilter (see auto-configuration ordering) so correlationId/actor
 * are already in the MDC by the time this filter runs.
 *
 * Publishing is async by default (see GovernanceHttpLoggingProperties.async) -
 * handed off to a bounded executor with the same fail-safe CallerRunsPolicy
 * shape used throughout this platform, so a slow log sink never adds
 * latency to the response. Set governance.http-logging.async=false to force
 * synchronous publishing instead.
 */
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpLoggingFilter.class);

    private final GovernanceHttpLoggingProperties properties;
    private final GovernanceCoreProperties coreProperties;
    private final SensitiveDataMasker masker;
    private final HttpAccessLogPublisher publisher;
    private final Executor asyncExecutor;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public HttpLoggingFilter(GovernanceHttpLoggingProperties properties,
                              GovernanceCoreProperties coreProperties,
                              SensitiveDataMasker masker,
                              HttpAccessLogPublisher publisher,
                              Executor asyncExecutor) {
        this.properties = properties;
        this.coreProperties = coreProperties;
        this.masker = masker;
        this.publisher = publisher;
        this.asyncExecutor = asyncExecutor;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return properties.getExcludedPaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path))
                || ThreadLocalRandom.current().nextDouble() >= properties.getSampleRate();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, 1024 * 1024);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        // Calling getParameterMap() here - BEFORE the request reaches the rest
        // of the chain - serves two purposes at once: (1) it's how we capture
        // query/form params for logging at all, and (2) it's the documented
        // workaround for a real ContentCachingRequestWrapper gotcha: for a
        // form-urlencoded POST body, the servlet container parses parameters
        // by consuming the input stream internally, which can race with this
        // wrapper's own body caching unless getParameterMap() is forced early.
        Map<String, String[]> parameterMap = properties.isIncludeParams() ? wrappedRequest.getParameterMap() : Map.of();

        long start = System.currentTimeMillis();
        Instant timestamp = Instant.now();

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long duration = System.currentTimeMillis() - start;

            String correlationId = MDC.get(coreProperties.getMdcKey());
            String actor = MDC.get(coreProperties.getActorMdcKey());
            if (actor == null || actor.isBlank()) {
                actor = "anonymous";
            }

            String requestHeaders = properties.isIncludeHeaders()
                    ? renderHeaders(headerNames(wrappedRequest), wrappedRequest::getHeader)
                    : null;
            String responseHeaders = properties.isIncludeHeaders()
                    ? renderHeaders(wrappedResponse.getHeaderNames(), wrappedResponse::getHeader)
                    : null;

            String queryString = properties.isIncludeParams() ? maskQueryString(wrappedRequest.getQueryString()) : null;
            String requestParameters = properties.isIncludeParams() ? renderParameters(parameterMap) : null;

            String requestBody = properties.isIncludeBody() ? readBody(wrappedRequest.getContentAsByteArray()) : null;
            String responseBody = properties.isIncludeBody() ? readBody(wrappedResponse.getContentAsByteArray()) : null;

            HttpAccessLogEvent event = new HttpAccessLogEvent(
                    correlationId, actor, wrappedRequest.getMethod(), wrappedRequest.getRequestURI(),
                    queryString, requestParameters, wrappedResponse.getStatus(),
                    duration, requestHeaders, responseHeaders, requestBody, responseBody, timestamp);

            publish(event);

            // MUST copy the cached body back to the real response - ContentCachingResponseWrapper
            // buffers output and does not write it through until this is called.
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void publish(HttpAccessLogEvent event) {
        if (!properties.isAsync()) {
            publisher.publish(event);
            return;
        }
        try {
            asyncExecutor.execute(() -> publisher.publish(event));
        } catch (RejectedExecutionException e) {
            // Only reachable if the executor itself is shut down (e.g. during
            // application shutdown) - CallerRunsPolicy handles ordinary
            // saturation before it ever gets here.
            log.warn("Async HTTP access log executor rejected task, publishing synchronously instead", e);
            publisher.publish(event);
        }
    }

    private Iterable<String> headerNames(HttpServletRequest request) {
        Enumeration<String> names = request.getHeaderNames();
        return names != null ? Collections.list(names) : Collections.emptyList();
    }

    private String renderHeaders(Iterable<String> names, java.util.function.Function<String, String> valueLookup) {
        StringBuilder sb = new StringBuilder("{");
        for (String name : names) {
            String value = valueLookup.apply(name);
            boolean sensitive = properties.getSensitiveHeaders().stream().anyMatch(name::equalsIgnoreCase);
            String rendered = (sensitive && coreProperties.isMaskSensitiveData())
                    ? masker.maskValue(value, properties.getSensitiveHeaderStrategy())
                    : value;
            sb.append(name).append("=").append(rendered).append(", ");
        }
        if (sb.length() > 1) {
            sb.setLength(sb.length() - 2);
        }
        return sb.append("}").toString();
    }

    private String renderParameters(Map<String, String[]> parameterMap) {
        StringBuilder sb = new StringBuilder("{");
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String name = entry.getKey();
            boolean sensitive = properties.getSensitiveParameterNames().stream().anyMatch(name::equalsIgnoreCase);
            for (String value : entry.getValue()) {
                String rendered = (sensitive && coreProperties.isMaskSensitiveData())
                        ? masker.maskValue(value, properties.getSensitiveHeaderStrategy())
                        : value;
                sb.append(name).append("=").append(rendered).append(", ");
            }
        }
        if (sb.length() > 1) {
            sb.setLength(sb.length() - 2);
        }
        return sb.append("}").toString();
    }

    /**
     * Masks the raw query string by name-matching each key against
     * sensitiveParameterNames, same as renderParameters but operating on the
     * literal "?key=value&key2=value2" string rather than the parsed map -
     * kept as a separate, simpler string (not reconstructed from the parsed
     * map) so what's logged matches what was literally on the wire.
     */
    private String maskQueryString(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return queryString;
        }
        StringBuilder result = new StringBuilder();
        for (String pair : queryString.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                result.append(pair).append('&');
                continue;
            }
            String name = pair.substring(0, eq);
            String value = pair.substring(eq + 1);
            boolean sensitive = properties.getSensitiveParameterNames().stream().anyMatch(name::equalsIgnoreCase);
            String rendered = (sensitive && coreProperties.isMaskSensitiveData())
                    ? masker.maskValue(value, properties.getSensitiveHeaderStrategy())
                    : value;
            result.append(name).append('=').append(rendered).append('&');
        }
        if (result.length() > 0) {
            result.setLength(result.length() - 1);
        }
        return result.toString();
    }

    private String readBody(byte[] content) {
        if (content == null || content.length == 0) {
            return "";
        }
        int limit = Math.min(content.length, properties.getMaxBodySize());
        String body = new String(content, 0, limit, StandardCharsets.UTF_8);
        String result = coreProperties.isMaskSensitiveData() ? maskBodyFields(body) : body;
        return content.length > limit ? result + "...(truncated)" : result;
    }

    /**
     * Best-effort, name-based masking of JSON "field":"value" pairs in a raw
     * body string. This is intentionally simple (no full JSON parse, so it
     * doesn't handle every nested/array shape) - it's a safety net for the
     * opt-in includeBody=true case, not a substitute for keeping includeBody
     * off and relying on @Auditable/@Sensitive on your actual DTOs.
     */
    private String maskBodyFields(String body) {
        String result = body;
        for (String field : properties.getSensitiveBodyFields()) {
            result = result.replaceAll(
                    "(?i)(\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*\")[^\"]*(\")",
                    "$1****$2");
        }
        return result;
    }
}
