package com.roconmachine.governance.idempotency.filter;

import com.roconmachine.governance.idempotency.IdempotencyStore;
import com.roconmachine.governance.idempotency.annotation.Idempotent;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private final IdempotencyStore store;
    private final RequestMappingHandlerMapping handlerMapping;

    public IdempotencyFilter(IdempotencyStore store, RequestMappingHandlerMapping handlerMapping) {
        this.store = store;
        this.handlerMapping = handlerMapping;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER);
        
        // Skip non-mutating requests or requests without key header
        if (idempotencyKey == null || idempotencyKey.isBlank() || isSafeMethod(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check if endpoint is annotated with @Idempotent
        HandlerMethod handlerMethod = getHandlerMethod(request);
        if (handlerMethod == null || !handlerMethod.hasMethodAnnotation(Idempotent.class)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Wrap stream readers for payload evaluation & response caching
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, 1024);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        // Force body caching
        wrappedRequest.getInputStream().readAllBytes();
        String fingerprint = calculateFingerprint(wrappedRequest);

        Optional<IdempotencyStore.IdempotencyRecord> existingRecord = store.getRecord(idempotencyKey);

        if (existingRecord.isPresent()) {
            IdempotencyStore.IdempotencyRecord record = existingRecord.get();

            // Validate fingerprint mismatch (same key, different body/path)
            if (!Objects.equals(record.requestFingerprint(), fingerprint)) {
                writeProblemDetail(response, HttpStatus.BAD_REQUEST, "Idempotency Key Payload Mismatch",
                        "The key has already been used for a different request payload.");
                return;
            }

            // Lock still held by concurrent execution
            if (record.state() == IdempotencyStore.State.PROCESSING) {
                writeProblemDetail(response, HttpStatus.CONFLICT, "Concurrent Request Processing",
                        "A request with this Idempotency-Key is currently being processed.");
                return;
            }

            // Replay cached response
            replayCachedResponse(response, record);
            return;
        }

        // Try acquire lock
        boolean locked = store.acquireLock(idempotencyKey, fingerprint);
        if (!locked) {
            writeProblemDetail(response, HttpStatus.CONFLICT, "Concurrent Request Processing",
                    "A request with this Idempotency-Key is currently being processed.");
            return;
        }

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);

            // Extract response status and body
            Map<String, String> headersToCache = extractResponseHeaders(wrappedResponse);
            byte[] responseBody = wrappedResponse.getContentInputStream().readAllBytes();

            store.saveResponse(idempotencyKey, fingerprint, wrappedResponse.getStatus(), headersToCache, responseBody);
            wrappedResponse.copyBodyToResponse(); // Flush output to client

        } catch (Exception ex) {
            throw ex;
        }
    }

    private boolean isSafeMethod(String method) {
        return Set.of("GET", "HEAD", "OPTIONS").contains(method.toUpperCase());
    }

    private String calculateFingerprint(ContentCachingRequestWrapper request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(request.getRequestURI().getBytes());
            digest.update(request.getContentAsByteArray());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 Algorithm not found", e);
        }
    }

    private HandlerMethod getHandlerMethod(HttpServletRequest request) {
        try {
            HandlerExecutionChain chain = handlerMapping.getHandler(request);
            if (chain != null && chain.getHandler() instanceof HandlerMethod hm) {
                return hm;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void replayCachedResponse(HttpServletResponse response, IdempotencyStore.IdempotencyRecord record) throws IOException {
        response.setStatus(record.status());
        record.headers().forEach(response::setHeader);
        response.setHeader("X-Cache-Lookup", "HIT-Idempotency");
        response.getOutputStream().write(record.responseBody());
        response.getOutputStream().flush();
    }

    private void writeProblemDetail(HttpServletResponse response, HttpStatus status, String title, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create("https://api.fintech.com/errors/" + status.value()));

        // Modern Java 21 string formatting output
        response.getWriter().write("""
            {
                "type": "%s",
                "title": "%s",
                "status": %d,
                "detail": "%s"
            }
            """.formatted(pd.getType(), pd.getTitle(), pd.getStatus(), pd.getDetail()));
    }

    private Map<String, String> extractResponseHeaders(HttpServletResponse response) {
        Map<String, String> headers = new HashMap<>();
        for (String headerName : response.getHeaderNames()) {
            headers.put(headerName, response.getHeader(headerName));
        }
        return headers;
    }
}