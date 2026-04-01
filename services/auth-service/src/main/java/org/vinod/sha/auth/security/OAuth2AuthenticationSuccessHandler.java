package org.vinod.sha.auth.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

        AuthResponse authResponse = authService.handleOAuth2Login(oauth2User, registrationId);

        String role = authResponse.getUser() != null ? authResponse.getUser().getRole() : null;
        String targetRedirectUri = HR_ROLES.contains(role) ? hrAdminSuccessRedirectUri : candidateSuccessRedirectUri;

        String redirectUrl = UriComponentsBuilder.fromUriString(targetRedirectUri)
                .queryParam("accessToken", authResponse.getAccessToken())
                .queryParam("refreshToken", authResponse.getRefreshToken())
                .queryParam("expiresIn", authResponse.getExpiresIn())
                .queryParam("provider", registrationId)
                .queryParam("role", role)
                .build(true)
                .toUriString();

        log.info("OAuth2 login success for provider '{}' and user '{}'", registrationId, authResponse.getUser().getUsername());
        response.sendRedirect(redirectUrl);
    }
}

