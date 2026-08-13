package com.platform.security.auth.filter;

import com.platform.governance.core.config.GovernanceCoreProperties;
import com.platform.security.auth.config.SecurityAuthProperties;
import com.platform.security.auth.model.AuthenticatedPrincipal;
import com.platform.security.auth.jwt.TokenValidationException;
import com.platform.security.auth.jwt.TokenValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Extracts the bearer token, validates it, and - on success - populates
 * Spring Security's SecurityContext with an authenticated principal whose
 * authorities are the caller's roles (as {@code ROLE_<role>} - the
 * convention {@code hasRole()}/{@code @PreAuthorize("hasRole(...)")} and
 * governance-rbac's own role checks both expect).
 *
 * Also writes the resolved subject into governance-core's actor MDC key, so
 * governance-audit and governance-http-logging automatically record the
 * real authenticated caller - closing the loop with the rest of this
 * platform without those modules needing any Spring Security dependency of
 * their own (they just read whatever is in that MDC key, from whatever
 * source populated it).
 *
 * A MISSING token always proceeds unauthenticated - this filter never
 * blocks public endpoints. A PRESENT-but-invalid token's behavior is
 * governed by {@code security.auth.reject-invalid-token}.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenValidator tokenValidator;
    private final SecurityAuthProperties properties;
    private final GovernanceCoreProperties coreProperties;

    public JwtAuthenticationFilter(TokenValidator tokenValidator,
                                    SecurityAuthProperties properties,
                                    GovernanceCoreProperties coreProperties) {
        this.tokenValidator = tokenValidator;
        this.properties = properties;
        this.coreProperties = coreProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            AuthenticatedPrincipal principal = tokenValidator.validate(token);
            SecurityContextHolder.getContext().setAuthentication(toAuthentication(principal));
            MDC.put(coreProperties.getActorMdcKey(), principal.getSubject());
        } catch (TokenValidationException e) {
            logger.debug("Token validation failed", e); // server-side log only - see TokenValidationException Javadoc
            if (properties.isRejectInvalidToken()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
                return;
            }
            // fall through unauthenticated - let downstream authorization rules decide
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(properties.getHeaderName());
        if (header == null || !header.startsWith(properties.getHeaderPrefix())) {
            return null;
        }
        return header.substring(properties.getHeaderPrefix().length()).trim();
    }

    private AbstractAuthenticationToken toAuthentication(AuthenticatedPrincipal principal) {
        List<GrantedAuthority> authorities = principal.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());

        AbstractAuthenticationToken authentication = new AbstractAuthenticationToken(authorities) {
            @Override
            public Object getCredentials() {
                return null; // never hold the raw token in the security context after validation
            }

            @Override
            public Object getPrincipal() {
                return principal;
            }
        };
        authentication.setAuthenticated(true);
        return authentication;
    }
}
