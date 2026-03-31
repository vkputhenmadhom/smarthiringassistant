package org.vinod.sha.gateway.graphql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Slf4j
@Controller
public class AuthGraphQLResolver {


    private final WebClient.Builder webClientBuilder;

    public AuthGraphQLResolver(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @QueryMapping
    public Map<String, Object> me() {
        // Resolved in security context — placeholder
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("id", "1");
        u.put("username", "current_user");
        u.put("email", "user@example.com");
        u.put("role", "CANDIDATE");
        return u;
    }

    @MutationMapping
    public Map<String, Object> register(
            @Argument String username,
            @Argument String email,
            @Argument String password,
            @Argument String role) {
        Map<String, Object> body = Map.of(
                "username", username,
                "email", email,
                "password", password,
                "role", role
        );
        try {
            Map<?, ?> result = webClientBuilder.build()
                    .post().uri("http://auth-service:8001/register")
                    .bodyValue(body)
                    .retrieve().bodyToMono(Map.class).block();
            return result != null ? (Map<String, Object>) result : stubAuthPayload(username, role);
        } catch (Exception e) {
            log.warn("auth-service unreachable during register: {}", e.getMessage());
            return stubAuthPayload(username, role);
        }
    }

    @MutationMapping
    public Map<String, Object> login(@Argument String username, @Argument String password) {
        Map<String, Object> body = Map.of("username", username, "password", password);
        try {
            Map<?, ?> result = webClientBuilder.build()
                    .post().uri("http://auth-service:8001/login")
                    .bodyValue(body)
                    .retrieve().bodyToMono(Map.class).block();
            return result != null ? (Map<String, Object>) result : stubAuthPayload(username, "CANDIDATE");
        } catch (Exception e) {
            log.warn("auth-service unreachable during login: {}", e.getMessage());
            return stubAuthPayload(username, "CANDIDATE");
        }
    }

    @MutationMapping
    public Map<String, Object> refreshToken(@Argument String refreshToken) {
        try {
            Map<?, ?> result = webClientBuilder.build()
                    .post().uri("http://auth-service:8001/refresh")
                    .header("Authorization", "Bearer " + refreshToken)
                    .retrieve().bodyToMono(Map.class).block();
            return result != null ? (Map<String, Object>) result : stubAuthPayload("user", "CANDIDATE");
        } catch (Exception e) {
            return stubAuthPayload("user", "CANDIDATE");
        }
    }

    @QueryMapping
    public List<Map<String, Object>> myNotifications(@Argument Boolean unreadOnly) {
        return List.of(
                Map.of("id", "1", "userId", "1", "type", "MATCH",
                        "title", "New match found!", "message", "You matched Senior Java Engineer (87%)",
                        "read", false, "createdAt", "2026-03-27T10:00:00Z")
        );
    }

    @MutationMapping
    public Map<String, Object> markNotificationRead(@Argument String id) {
        return Map.of("id", id, "userId", "1", "type", "MATCH",
                "title", "New match found!", "message", "...", "read", true,
                "createdAt", "2026-03-27T10:00:00Z");
    }

    @MutationMapping
    public Boolean markAllNotificationsRead() {
        return true;
    }

    private Map<String, Object> stubAuthPayload(String username, String role) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", "1");
        user.put("username", username);
        user.put("email", username + "@example.com");
        user.put("role", role);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", "stub-jwt-token");
        payload.put("refreshToken", "stub-refresh-token");
        payload.put("expiresIn", 3600);
        payload.put("user", user);
        return payload;
    }
}

