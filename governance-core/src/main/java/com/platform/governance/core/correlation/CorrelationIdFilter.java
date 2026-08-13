package com.platform.governance.core.correlation;

import com.platform.governance.core.config.GovernanceCoreProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Guarantees every request has a correlation id (generating one if the
 * caller didn't supply it) and, if present, an actor id - both placed in the
 * MDC so every downstream governance concern (audit events, HTTP access
 * logs, application logs) sees the identical values without redoing this
 * work themselves. No Spring Security dependency: actor comes from a plain
 * configurable header, so this filter (and everything built on it) works in
 * any service regardless of auth mechanism.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    private final GovernanceCoreProperties properties;

    public CorrelationIdFilter(GovernanceCoreProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = properties.getCorrelationIdHeader();
        String correlationId = request.getHeader(header);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        String actor = request.getHeader(properties.getActorHeader());

        MDC.put(properties.getMdcKey(), correlationId);
        if (actor != null && !actor.isBlank()) {
            MDC.put(properties.getActorMdcKey(), actor);
        }
        response.setHeader(header, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(properties.getMdcKey());
            MDC.remove(properties.getActorMdcKey());
        }
    }
}
