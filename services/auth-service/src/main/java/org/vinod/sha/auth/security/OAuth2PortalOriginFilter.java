package org.vinod.sha.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Retained as a no-op bean for backward compatibility.
 *
 * <p>The portal-origin hint is now carried through the OAuth2 state/session via
 * {@link PortalAwareAuthorizationRequestResolver} and
 * {@link PortalAwareAuthorizationRequestRepository}.  No cookie is written or read here.
 *
 * @deprecated Cookie-based portal tracking replaced by state-based session tracking.
 */
@Deprecated(forRemoval = true)
@Component
public class OAuth2PortalOriginFilter extends OncePerRequestFilter {

    public static final String PORTAL_CANDIDATE = "candidate";
    public static final String PORTAL_HR_ADMIN   = "hr-admin";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // no-op: portal routing is now handled via OAuth2 state/session attributes
        filterChain.doFilter(request, response);
    }
}
