package org.vinod.sha.auth.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;

    @Value("${app.oauth2.success-redirect-uri:http://localhost:5173/auth/callback}")
    private String successRedirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauth2User = oauthToken.getPrincipal();
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();

        AuthResponse authResponse = authService.handleOAuth2Login(oauth2User, registrationId);

        String redirectUrl = UriComponentsBuilder.fromUriString(successRedirectUri)
                .queryParam("accessToken", authResponse.getAccessToken())
                .queryParam("refreshToken", authResponse.getRefreshToken())
                .queryParam("expiresIn", authResponse.getExpiresIn())
                .queryParam("provider", registrationId)
                .build(true)
                .toUriString();

        log.info("OAuth2 login success for provider '{}' and user '{}'", registrationId, authResponse.getUser().getUsername());
        response.sendRedirect(redirectUrl);
    }
}

