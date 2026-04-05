package org.vinod.sha.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.util.UriComponentsBuilder;
import org.vinod.sha.auth.repository.UserRepository;
import org.vinod.sha.auth.security.JwtAuthenticationFilter;
import org.vinod.sha.auth.security.JwtTokenProvider;
import org.vinod.sha.auth.security.OAuth2AuthenticationSuccessHandler;
import org.vinod.sha.auth.security.PortalAwareAuthorizationRequestRepository;
import org.vinod.sha.auth.security.PortalAwareAuthorizationRequestResolver;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    public SecurityConfiguration(JwtTokenProvider tokenProvider, UserRepository userRepository) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
    }

    @Value("${app.oauth2.failure-redirect-uri:http://localhost:5173/login}")
    private String failureRedirectUri;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler,
                                           PortalAwareAuthorizationRequestResolver portalResolver,
                                           PortalAwareAuthorizationRequestRepository portalRepository) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\": \"Unauthorized\"}");
                        }))
                // OAuth2 needs a session – state-based portal hint lives there
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/register", "/login", "/refresh", "/validate").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers("/actuator/**", "/error").permitAll()
                        .requestMatchers("/clear-session").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(ae -> ae
                                // embed ?portal= into the auth-request attributes (session-stored)
                                .authorizationRequestResolver(portalResolver)
                                // on callback: copy portal attribute into well-known session key
                                .authorizationRequestRepository(portalRepository))
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                        .failureHandler((request, response, exception) -> {
                            String redirectUrl = UriComponentsBuilder.fromUriString(failureRedirectUri)
                                    .queryParam("error", "oauth2_login_failed")
                                    .build(true)
                                    .toUriString();
                            response.sendRedirect(redirectUrl);
                        }))
                // JWT filter for API calls – no cookie filter needed any more
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(tokenProvider, userRepository);
    }
}
