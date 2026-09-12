package com.roconmachine.securityauth.extension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;

/**
 * Fallback {@link UserAuthenticationProvider} registered only when the host application
 * supplies no bean of its own. Trusts the token's subject/claims verbatim with a single
 * {@code ROLE_USER} authority and no user-store lookup — fine for prototyping, wrong
 * for production. Register a {@code @Bean UserAuthenticationProvider} in the host app
 * to replace this.
 */
public class DefaultUserAuthenticationProvider implements UserAuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultUserAuthenticationProvider.class);

    @Override
    public UserDetails loadUser(String subject, Map<String, Object> claims) {
        log.warn("Using DefaultUserAuthenticationProvider for subject [{}] - this trusts token "
                + "claims with no user-store lookup and MUST be overridden with a custom "
                + "UserAuthenticationProvider bean before going to production.", subject);
        return User.withUsername(subject)
                .password("")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
    }
}
