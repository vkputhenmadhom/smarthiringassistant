package org.vinod.sha.auth.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.vinod.sha.auth.dto.AuthResponse;
import org.vinod.sha.auth.service.AuthService;

import java.io.IOException;
import java.util.Set;

/**
 * Redirects the browser to the correct portal after a successful OAuth2 login.
 *
 * <p>Portal origin is read from the HTTP session key set by
 * {@link PortalAwareAuthorizationRequestRepository} – no cookies, no host/port
 * assumptions.  Role is used only as a fallback when the session key is absent.
 */
@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    private final AuthService authService;

    public OAuth2AuthenticationSuccessHandler(AuthService authService) {
        this.authService = authService;
    }

    @Value("${app.oauth2.success-redirect-uri-candidate:http://localhost:5173/auth/callback}")
    private String candidateSuccessRedirectUri;

    @Value("${app.oauth2.success-redirect-uri-hr-admin:http://localhost:4200/auth/callback}")
    private String hrAdminSuccessRedirectUri;

    private static final Set<String> HR_ROLES = Set.of("ADMIN", "RECRUITER", "HIRING_MANAGER");

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauth2User = oauthToken.getPrincipal();
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();

        // Resolve the portal FIRST so we can pass it to AuthService for correct role assignment.
        String portalOrigin = popPortalFromSession(request);
        AuthResponse authResponse = authService.handleOAuth2Login(oauth2User, registrationId, portalOrigin);
        String role = authResponse.getUser() != null ? authResponse.getUser().getRole() : null;

        // --- state-based portal resolution (no cookies) ---
        String targetRedirectUri = resolveTargetRedirectUri(portalOrigin, role);

        String redirectUrl = UriComponentsBuilder.fromUriString(targetRedirectUri)
                .queryParam("accessToken", authResponse.getAccessToken())
                .queryParam("refreshToken", authResponse.getRefreshToken())
                .queryParam("expiresIn", authResponse.getExpiresIn())
                .queryParam("provider", registrationId)
                .queryParam("role", role)
                .build(true)
                .toUriString();

        log.info("OAuth2 login success – provider='{}' user='{}' role='{}' portal='{}' → {}",
                registrationId,
                authResponse.getUser().getUsername(),
                role,
                portalOrigin != null ? portalOrigin : "role-fallback",
                targetRedirectUri);

        response.sendRedirect(redirectUrl);
    }

    /** Reads and removes the portal hint from the server-side session. */
    private String popPortalFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(PortalAwareAuthorizationRequestRepository.PORTAL_SESSION_KEY);
        session.removeAttribute(PortalAwareAuthorizationRequestRepository.PORTAL_SESSION_KEY);
        return value instanceof String s ? s : null;
    }

    private String resolveTargetRedirectUri(String portalOrigin, String role) {
        if ("hr-admin".equals(portalOrigin)) {
            return hrAdminSuccessRedirectUri;
        }
        if ("candidate".equals(portalOrigin)) {
            return candidateSuccessRedirectUri;
        }
        // role-based fallback – used only when ?portal= was not supplied
        return HR_ROLES.contains(role) ? hrAdminSuccessRedirectUri : candidateSuccessRedirectUri;
    }
}
