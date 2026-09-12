package com.roconmachine.securityauth.filter;

import com.roconmachine.securityauth.autoconfigure.JwtSecurityProperties;
import com.roconmachine.securityauth.core.JwtService;
import com.roconmachine.securityauth.extension.UserAuthenticationProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Extracts a Bearer token on every request, validates it, and — on success — populates
 * the {@link SecurityContextHolder}. Requests with no token, a malformed token, or a
 * path matching {@code jwt.security.permit-all-patterns} simply continue the chain
 * unauthenticated; it is downstream authorization rules (or {@code anyRequest().authenticated()}
 * in the default chain) that ultimately reject them.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtService jwtService;
    private final JwtSecurityProperties properties;
    private final UserAuthenticationProvider userAuthenticationProvider;

    public JwtAuthenticationFilter(JwtService jwtService,
                                    JwtSecurityProperties properties,
                                    UserAuthenticationProvider userAuthenticationProvider) {
        this.jwtService = jwtService;
        this.properties = properties;
        this.userAuthenticationProvider = userAuthenticationProvider;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        List<String> patterns = List.of(properties.getPermitAllPatterns());
        return patterns.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(properties.getHeaderName());
        String prefix = properties.getHeaderPrefix();

        if (header == null || !header.startsWith(prefix)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(prefix.length());

        try {
            Claims claims = jwtService.parseClaims(token);
            String subject = claims.getSubject();

            if (subject != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userAuthenticationProvider.loadUser(subject, claims);

                if (userDetails != null) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    log.debug("UserAuthenticationProvider returned null for subject [{}]; "
                            + "leaving request unauthenticated", subject);
                }
            }
        } catch (Exception e) {
            // Malformed/expired/mis-signed token: log and continue unauthenticated rather
            // than failing the request here — lets permitAll endpoints still work even
            // with a stale Authorization header, and lets Spring Security's own
            // AuthenticationEntryPoint produce the 401/403 for protected ones.
            log.debug("JWT validation failed, continuing unauthenticated: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
