package org.vinod.sha.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Custom OAuth2 authorization-request resolver that reads an optional {@code ?portal=}
 * query parameter from the incoming request and embeds it into
 * {@link OAuth2AuthorizationRequest#getAttributes()} so it survives the Google/LinkedIn
 * round-trip through the HTTP session – entirely host/port-agnostic.
 *
 * <p>No cookies are used; the portal hint travels with the auth-request object which
 * Spring Security stores in the server-side session (keyed by {@code state}).
 */
@Component
public class PortalAwareAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    /** Session/attribute key used to carry the portal hint. */
    public static final String PORTAL_ATTR = "portal";

    private static final Set<String> ALLOWED_PORTALS = Set.of("candidate", "hr-admin");

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public PortalAwareAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, "/oauth2/authorization");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return customize(delegate.resolve(request), request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return customize(delegate.resolve(request, clientRegistrationId), request);
    }

    private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest authRequest,
                                                  HttpServletRequest request) {
        if (authRequest == null) {
            return null;
        }
        String portal = normalizePortal(request.getParameter(PORTAL_ATTR));
        if (portal == null) {
            return authRequest;
        }
        Map<String, Object> attributes = new HashMap<>(authRequest.getAttributes());
        attributes.put(PORTAL_ATTR, portal);
        return OAuth2AuthorizationRequest.from(authRequest)
                .attributes(attributes)
                .build();
    }

    private String normalizePortal(String portal) {
        if (portal == null || portal.isBlank()) {
            return null;
        }
        String normalized = portal.trim().toLowerCase(Locale.ROOT);
        return ALLOWED_PORTALS.contains(normalized) ? normalized : null;
    }
}

