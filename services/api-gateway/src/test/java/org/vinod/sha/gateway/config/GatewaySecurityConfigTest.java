package org.vinod.sha.gateway.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.EnableWebFlux;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/** Exercises the real HTTP security filter chain without Redis, RabbitMQ or downstream services. */
@SpringJUnitConfig(GatewaySecurityConfigTest.TestConfig.class)
@TestPropertySource(properties = "jwt.secret=" + GatewaySecurityConfigTest.SECRET)
class GatewaySecurityConfigTest {

    static final String SECRET = "test-secret-0123456789-0123456789-0123456789-0123456789-abcdef";

    @Autowired
    private ApplicationContext context;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context).build();
    }

    @Test
    void rejectsRoutedRequestWithoutToken() {
        client.get().uri("/api/resumes/1").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void rejectsRoutedRequestWithForgedToken() {
        String forged = token("attacker-secret-0123456789-0123456789-0123456789-0123456789-xyz", 60_000);

        client.get().uri("/api/resumes/1").headers(h -> h.setBearerAuth(forged))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void rejectsRoutedRequestWithExpiredToken() {
        client.get().uri("/api/resumes/1").headers(h -> h.setBearerAuth(token(SECRET, -60_000)))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void allowsRoutedRequestWithAuthServiceToken() {
        client.get().uri("/api/resumes/1").headers(h -> h.setBearerAuth(token(SECRET, 60_000)))
                .exchange().expectStatus().isOk();
    }

    @Test
    void ignoresExpiredTokenOnPublicAuthPath() {
        client.post().uri("/api/auth/login").headers(h -> h.setBearerAuth(token(SECRET, -60_000)))
                .exchange().expectStatus().isOk();
    }

    @Test
    void leavesGraphqlToTheOperationInterceptor() {
        client.post().uri("/graphql").exchange().expectStatus().isOk();
    }

    @Test
    void exposesHealthButNotOtherActuatorEndpoints() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
        client.get().uri("/actuator/env").exchange().expectStatus().isUnauthorized();
    }

    private static String token(String secret, long expiresInMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject("user")
                .claims(Map.of("id", 42L, "role", "JOB_SEEKER"))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiresInMs))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Configuration
    @EnableWebFlux
    @Import({GatewaySecurityConfig.class, StubController.class})
    static class TestConfig {
    }

    /** Stands in for the routed services and endpoints behind the security chain. */
    @RestController
    static class StubController {

        @GetMapping({"/api/resumes/1", "/actuator/health", "/actuator/env"})
        String get() {
            return "ok";
        }

        @PostMapping({"/api/auth/login", "/graphql"})
        String post() {
            return "ok";
        }
    }
}
