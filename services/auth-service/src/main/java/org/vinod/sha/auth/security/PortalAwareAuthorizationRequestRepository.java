package org.vinod.sha.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * Wraps Spring's default session-based {@link AuthorizationRequestRepository} and, when
 * an authorization request is removed (i.e. the OAuth callback is being processed),
 * copies the {@code portal} attribute out of the request object into a predictable
 * session key so the success handler can read it after Spring Security has consumed
 * (and deleted) the original request entry.
 *
 * <p>This is fully host/port-agnostic: no cookies are written; everything lives in the
 * server-side HTTP session that Spring Security already maintains.
 */
@Component
public class PortalAwareAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    /** Session key used to hand the portal hint from the repository to the success handler. */
    public static final String PORTAL_SESSION_KEY = "sha_oauth2_portal_origin";

    private final HttpSessionOAuth2AuthorizationRequestRepository delegate =
            new HttpSessionOAuth2AuthorizationRequestRepository();

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return delegate.loadAuthorizationRequest(request);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
        delegate.saveAuthorizationRequest(authorizationRequest, request, response);
    }

    /**
     * Called by {@code OAuth2LoginAuthenticationFilter} before the success handler is
     * invoked.  We stash the portal value into the session so the success handler can
     * retrieve it even though the auth-request entry has been removed.
     */
    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                  HttpServletResponse response) {
        OAuth2AuthorizationRequest authRequest = delegate.removeAuthorizationRequest(request, response);
        if (authRequest != null) {
            Object portal = authRequest.getAttribute(PortalAwareAuthorizationRequestResolver.PORTAL_ATTR);
            HttpSession session = request.getSession(false);
            if (session != null) {
                if (portal instanceof String p && !p.isBlank()) {
                    session.setAttribute(PORTAL_SESSION_KEY, p);
                } else {
                    session.removeAttribute(PORTAL_SESSION_KEY);
                }
            }
        }
        return authRequest;
    }
}

