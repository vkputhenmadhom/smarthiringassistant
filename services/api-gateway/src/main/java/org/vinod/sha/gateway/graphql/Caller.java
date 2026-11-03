package org.vinod.sha.gateway.graphql;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.util.Set;

/** Who is calling a resolver, from the authentication GraphQLAuthorizationInterceptor puts in the Reactor context. */
record Caller(Long userId, boolean hr) {

    private static final Set<String> HR_ROLES = Set.of("ROLE_HR_ADMIN", "ROLE_RECRUITER");

    static Mono<Caller> current() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Caller::from)
                .defaultIfEmpty(new Caller(null, false));
    }

    static Caller from(Authentication authentication) {
        boolean hr = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(HR_ROLES::contains);
        Long userId = null;
        if (authentication instanceof JwtAuthenticationToken jwt) {
            Object id = jwt.getToken().getClaims().get("id");
            if (id instanceof Number n) {
                userId = n.longValue();
            } else if (id != null) {
                try {
                    userId = Long.parseLong(String.valueOf(id));
                } catch (NumberFormatException ignored) {
                    // a token without a numeric id identifies nobody
                }
            }
        }
        return new Caller(userId, hr);
    }

    /** HR sees everything; anyone else only records that belong to them. */
    boolean mayRead(Object ownerId) {
        return hr || (userId != null && ownerId != null && userId.toString().equals(String.valueOf(ownerId)));
    }
}
