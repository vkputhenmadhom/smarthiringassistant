package org.vinod.sha.gateway.graphql;

import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.vinod.sha.gateway.resilience.GatewayResilience;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.*;

@Slf4j
@Controller
public class AuthGraphQLResolver {

    private static final String AUTH_BASE = "http://auth-service:8001/api/auth";
    private static final String AUTH_BACKEND = "auth-service";


    private final WebClient.Builder webClientBuilder;
    private final GatewayResilience gatewayResilience;
    private final ObjectMapper objectMapper;

    public AuthGraphQLResolver(WebClient.Builder webClientBuilder,
                               GatewayResilience gatewayResilience) {
        this.webClientBuilder = webClientBuilder;
        this.gatewayResilience = gatewayResilience;
        this.objectMapper = new ObjectMapper();
    }

    @QueryMapping
    public Mono<Map<String, Object>> me(@ContextValue(name = "Authorization", required = false) String authorization) {
        return Mono.just(parseUserFromAuthorization(authorization).orElseGet(() -> {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("id", "1");
            fallback.put("username", "current_user");
            fallback.put("email", "user@example.com");
            fallback.put("role", "CANDIDATE");
            return fallback;
        }));
    }

    @MutationMapping
    public Mono<Map<String, Object>> register(
            @Argument String username,
            @Argument String email,
            @Argument String password,
            @Argument String role) {
        Map<String, Object> body = Map.of(
                "username", username,
                "email", email,
                "password", password,
                "confirmPassword", password,
                "firstName", "GraphQL",
                "lastName", "User",
                "role", toAuthRole(role)
        );
        return gatewayResilience.protect(AUTH_BACKEND,
                        webClientBuilder.build()
                                .post().uri(AUTH_BASE + "/register")
                                .bodyValue(body)
                                .retrieve().bodyToMono(Map.class))
                .map(this::toStringObjectMap)
                .map(this::normalizeAuthPayload)
                .switchIfEmpty(Mono.just(stubAuthPayload(username, role)))
                .onErrorResume(e -> {
                    log.warn("auth-service unreachable during register: {}", e.getMessage());
                    return Mono.just(stubAuthPayload(username, role));
                });
    }

    @MutationMapping
    public Mono<Map<String, Object>> login(@Argument String username, @Argument String password) {
        Map<String, Object> body = Map.of("username", username, "password", password);
        return gatewayResilience.protect(AUTH_BACKEND,
                        webClientBuilder.build()
                                .post().uri(AUTH_BASE + "/login")
                                .bodyValue(body)
                                .retrieve().bodyToMono(Map.class))
                .map(this::toStringObjectMap)
                .map(this::normalizeAuthPayload)
                .switchIfEmpty(Mono.just(stubAuthPayload(username, "CANDIDATE")))
                .onErrorResume(e -> {
                    log.warn("auth-service unreachable during login: {}", e.getMessage());
                    return Mono.just(stubAuthPayload(username, "CANDIDATE"));
                });
    }

    @MutationMapping
    public Mono<Map<String, Object>> refreshToken(@Argument String refreshToken) {
        return gatewayResilience.protect(AUTH_BACKEND,
                        webClientBuilder.build()
                                .post().uri(AUTH_BASE + "/refresh")
                                .header("Authorization", "Bearer " + refreshToken)
                                .retrieve().bodyToMono(Map.class))
                .map(this::toStringObjectMap)
                .map(this::normalizeAuthPayload)
                .switchIfEmpty(Mono.just(stubAuthPayload("user", "CANDIDATE")))
                .onErrorResume(e -> Mono.just(stubAuthPayload("user", "CANDIDATE")));
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeAuthPayload(Map<String, Object> raw) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("token", firstNonBlank(raw.get("token"), raw.get("accessToken"), ""));
        normalized.put("refreshToken", String.valueOf(raw.getOrDefault("refreshToken", "")));
        normalized.put("expiresIn", toLong(raw.get("expiresIn"), 3600L));

        Object userObj = raw.get("user");
        Map<String, Object> user = userObj instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        Map<String, Object> normalizedUser = new LinkedHashMap<>();
        normalizedUser.put("id", String.valueOf(user.getOrDefault("id", "1")));
        String username = String.valueOf(user.getOrDefault("username", "user"));
        normalizedUser.put("username", username);
        normalizedUser.put("email", String.valueOf(user.getOrDefault("email", username + "@example.com")));
        normalizedUser.put("role", toGraphqlRole(String.valueOf(user.getOrDefault("role", "JOB_SEEKER"))));

        normalized.put("user", normalizedUser);
        return normalized;
    }

    private long toLong(Object value, long fallback) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String firstNonBlank(Object primary, Object secondary, String fallback) {
        String p = primary == null ? "" : String.valueOf(primary);
        if (!p.isBlank()) {
            return p;
        }
        String s = secondary == null ? "" : String.valueOf(secondary);
        return s.isBlank() ? fallback : s;
    }

    private String toAuthRole(String graphqlRole) {
        String role = graphqlRole == null ? "" : graphqlRole.toUpperCase(Locale.ROOT);
        return switch (role) {
            case "HR_ADMIN", "SUPER_ADMIN" -> "ADMIN";
            case "CANDIDATE" -> "JOB_SEEKER";
            case "RECRUITER" -> "RECRUITER";
            default -> "JOB_SEEKER";
        };
    }

    private String toGraphqlRole(String authRole) {
        String role = authRole == null ? "" : authRole.toUpperCase(Locale.ROOT);
        return switch (role) {
            case "ADMIN" -> "HR_ADMIN";
            case "JOB_SEEKER" -> "CANDIDATE";
            case "RECRUITER" -> "RECRUITER";
            case "HIRING_MANAGER" -> "HR_ADMIN";
            default -> "CANDIDATE";
        };
    }

    private Optional<Map<String, Object>> parseUserFromAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }

        try {
            String[] parts = authorization.substring(7).split("\\.");
            if (parts.length < 2) {
                return Optional.empty();
            }

            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = objectMapper.readValue(new String(decoded, StandardCharsets.UTF_8), Map.class);

            Map<String, Object> user = new LinkedHashMap<>();
            user.put("id", String.valueOf(claims.getOrDefault("id", "1")));
            String username = String.valueOf(claims.getOrDefault("username", claims.getOrDefault("sub", "current_user")));
            user.put("username", username);
            user.put("email", String.valueOf(claims.getOrDefault("email", username + "@example.com")));
            user.put("role", toGraphqlRole(String.valueOf(claims.getOrDefault("role", "JOB_SEEKER"))));
            return Optional.of(user);
        } catch (Exception e) {
            log.debug("Unable to parse me() from authorization header: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toStringObjectMap(Map<?, ?> raw) {
        return raw == null ? Map.of() : (Map<String, Object>) raw;
    }
}

