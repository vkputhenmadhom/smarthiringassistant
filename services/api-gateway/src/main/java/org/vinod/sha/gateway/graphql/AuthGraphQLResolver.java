package org.vinod.sha.gateway.graphql;

import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.vinod.sha.gateway.resilience.GatewayResilience;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
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
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(body)
                                .retrieve().bodyToMono(Map.class))
                .map(this::toStringObjectMap)
                .map(raw -> {
                    if (String.valueOf(raw.getOrDefault("accessToken", "")).isBlank()) {
                        throw new AuthFlowException(AuthFlowException.PENDING_APPROVAL,
                                "Account created. An administrator must approve it before you can sign in.");
                    }
                    return normalizeAuthPayload(raw);
                })
                .onErrorMap(this::toAuthFlowException);
    }

    @MutationMapping
    public Mono<Map<String, Object>> login(@Argument String username, @Argument String password) {
        Map<String, Object> body = Map.of("username", username, "password", password);
        return gatewayResilience.protect(AUTH_BACKEND,
                        webClientBuilder.build()
                                .post().uri(AUTH_BASE + "/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(body)
                                .retrieve().bodyToMono(Map.class))
                .map(this::toStringObjectMap)
                .map(this::normalizeAuthPayload)
                .onErrorMap(this::toAuthFlowException);
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
                .onErrorMap(this::toAuthFlowException);
    }

    // ── Account approval (ADMIN only; enforced by GraphQLAuthorizationInterceptor and auth-service) ──

    @QueryMapping
    public Mono<List<Map<String, Object>>> pendingUsers(
            @ContextValue(name = "Authorization", required = false) String authorization) {
        return gatewayResilience.protect(AUTH_BACKEND,
                        webClientBuilder.build()
                                .get().uri(AUTH_BASE + "/admin/users?status=PENDING")
                                .header(HttpHeaders.AUTHORIZATION, authorization)
                                .retrieve().bodyToFlux(Map.class).collectList())
                .map(users -> users.stream().map(user -> toGraphqlUser(toStringObjectMap(user))).toList())
                .onErrorMap(this::toAuthFlowException);
    }

    @MutationMapping
    public Mono<Map<String, Object>> approveUser(@Argument String id, @Argument String role,
                                                 @ContextValue(name = "Authorization", required = false) String authorization) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(AUTH_BASE + "/admin/users/{id}/approve");
        if (role != null) {
            uri.queryParam("role", toAuthRole(role));
        }
        return decideOnUser(uri.buildAndExpand(id).toUriString(), authorization);
    }

    @MutationMapping
    public Mono<Map<String, Object>> rejectUser(@Argument String id,
                                                @ContextValue(name = "Authorization", required = false) String authorization) {
        return decideOnUser(UriComponentsBuilder.fromUriString(AUTH_BASE + "/admin/users/{id}/reject")
                .buildAndExpand(id).toUriString(), authorization);
    }

    private Mono<Map<String, Object>> decideOnUser(String uri, String authorization) {
        return gatewayResilience.protect(AUTH_BACKEND,
                        webClientBuilder.build()
                                .post().uri(uri)
                                .header(HttpHeaders.AUTHORIZATION, authorization)
                                .retrieve().bodyToMono(Map.class))
                .map(user -> toGraphqlUser(toStringObjectMap(user)))
                .onErrorMap(this::toAuthFlowException);
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeAuthPayload(Map<String, Object> raw) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("token", firstNonBlank(raw.get("token"), raw.get("accessToken"), ""));
        normalized.put("refreshToken", String.valueOf(raw.getOrDefault("refreshToken", "")));
        normalized.put("expiresIn", toLong(raw.get("expiresIn"), 3600L));

        Object userObj = raw.get("user");
        Map<String, Object> user = userObj instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        normalized.put("user", toGraphqlUser(user));
        return normalized;
    }

    private Map<String, Object> toGraphqlUser(Map<String, Object> user) {
        Map<String, Object> normalizedUser = new LinkedHashMap<>();
        normalizedUser.put("id", String.valueOf(user.getOrDefault("id", "1")));
        String username = String.valueOf(user.getOrDefault("username", "user"));
        normalizedUser.put("username", username);
        normalizedUser.put("email", String.valueOf(user.getOrDefault("email", username + "@example.com")));
        normalizedUser.put("role", toGraphqlRole(String.valueOf(user.getOrDefault("role", "JOB_SEEKER"))));
        if (user.get("approvalStatus") != null) {
            normalizedUser.put("approvalStatus", String.valueOf(user.get("approvalStatus")));
        }
        toOffsetDateTime(user.get("createdAt")).ifPresent(createdAt -> normalizedUser.put("createdAt", createdAt));
        return normalizedUser;
    }

    /** auth-service sends LocalDateTime without an offset; its containers run in UTC. */
    private Optional<OffsetDateTime> toOffsetDateTime(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(String.valueOf(value)).atOffset(ZoneOffset.UTC));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * Turns auth-service failures into client-facing GraphQL errors: its 4xx messages
     * (wrong password, pending approval, validation) are shown as-is; anything else means
     * the service is unavailable.
     */
    private Throwable toAuthFlowException(Throwable error) {
        if (error instanceof AuthFlowException) {
            return error;
        }
        if (error instanceof WebClientResponseException response && response.getStatusCode().is4xxClientError()) {
            ErrorType type = switch (HttpStatus.valueOf(response.getStatusCode().value())) {
                case UNAUTHORIZED -> ErrorType.UNAUTHORIZED;
                case FORBIDDEN -> ErrorType.FORBIDDEN;
                case NOT_FOUND -> ErrorType.NOT_FOUND;
                default -> ErrorType.BAD_REQUEST;
            };
            return new AuthFlowException(type, readableMessage(response));
        }
        log.warn("auth-service call failed: {}", error.toString());
        return new AuthFlowException(ErrorType.INTERNAL_ERROR, "Authentication service is unavailable. Please try again.");
    }

    @SuppressWarnings("unchecked")
    private String readableMessage(WebClientResponseException response) {
        String body = response.getResponseBodyAsString(StandardCharsets.UTF_8).trim();
        if (body.startsWith("{")) {
            try {
                // Validation errors arrive as {"field": "message", ...}; {"error": "..."} from security handlers
                Map<String, Object> fields = objectMapper.readValue(body, Map.class);
                return fields.values().stream().map(String::valueOf).reduce((a, b) -> a + "; " + b).orElse(body);
            } catch (Exception ignored) {
                return body;
            }
        }
        return body.isEmpty() ? response.getStatusText() : body;
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

