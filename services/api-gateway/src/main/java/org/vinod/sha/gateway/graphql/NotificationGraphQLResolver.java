package org.vinod.sha.gateway.graphql;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.client.WebClient;
import org.vinod.sha.gateway.resilience.GatewayResilience;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The signed-in user's notification bell, stored by notification-service. Every call uses the caller's own id
 * from the token, so nobody reads or marks another user's notifications.
 */
@Controller
public class NotificationGraphQLResolver {

    private static final String NOTIFICATION_BASE = "http://notification-service:8007/api/notifications/in-app";
    private static final String NOTIFICATION_BACKEND = "notification-service";

    private final WebClient.Builder webClientBuilder;
    private final GatewayResilience gatewayResilience;

    public NotificationGraphQLResolver(WebClient.Builder webClientBuilder, GatewayResilience gatewayResilience) {
        this.webClientBuilder = webClientBuilder;
        this.gatewayResilience = gatewayResilience;
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<List<Map<String, Object>>> myNotifications(@Argument Boolean unreadOnly) {
        return callerId().flatMap(userId -> gatewayResilience.protect(NOTIFICATION_BACKEND,
                                webClientBuilder.build().get()
                                        .uri(NOTIFICATION_BASE + "/users/" + userId + "?unreadOnly=" + Boolean.TRUE.equals(unreadOnly))
                                        .retrieve().bodyToFlux(Map.class)
                                        .map(NotificationGraphQLResolver::notification)
                                        .collectList()))
                .onErrorMap(e -> e instanceof ClientFacingException ? e : BackendErrors.read("notification service", "your notifications", e));
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<Map<String, Object>> markNotificationRead(@Argument String id) {
        return callerId().flatMap(userId -> gatewayResilience.protectWrite(NOTIFICATION_BACKEND,
                                webClientBuilder.build().post()
                                        .uri(NOTIFICATION_BASE + "/users/" + userId + "/" + id + "/read")
                                        .retrieve().bodyToMono(Map.class)
                                        .map(NotificationGraphQLResolver::notification)))
                .onErrorMap(e -> e instanceof ClientFacingException ? e
                        : BackendErrors.write("notification service", "mark as read", "notification " + id, "Reload your notifications", e));
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> markAllNotificationsRead() {
        return callerId().flatMap(userId -> gatewayResilience.protectWrite(NOTIFICATION_BACKEND,
                                webClientBuilder.build().post()
                                        .uri(NOTIFICATION_BASE + "/users/" + userId + "/read-all")
                                        .retrieve().bodyToMono(Map.class)
                                        .thenReturn(true)))
                .onErrorMap(e -> e instanceof ClientFacingException ? e
                        : BackendErrors.write("notification service", "mark as read", "your notifications", "Reload your notifications", e));
    }

    private static Mono<Long> callerId() {
        return Caller.current().flatMap(caller -> caller.userId() == null
                ? Mono.error(new ClientFacingException(ErrorType.UNAUTHORIZED, "Sign in to see your notifications."))
                : Mono.just(caller.userId()));
    }

    /** notification-service's JSON → the GraphQL Notification type */
    static Map<String, Object> notification(Map<?, ?> raw) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", String.valueOf(raw.get("id")));
        n.put("userId", String.valueOf(raw.get("userId")));
        n.put("type", Objects.toString(raw.get("type"), "INFO"));
        n.put("title", Objects.toString(raw.get("title"), ""));
        n.put("message", Objects.toString(raw.get("message"), ""));
        n.put("read", Boolean.TRUE.equals(raw.get("read")));
        n.put("createdAt", utc(raw.get("createdAt")));
        return n;
    }

    /** The service's local time (UTC in the containers) as an ISO instant; null stays null */
    private static String utc(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        try {
            return OffsetDateTime.parse(text).toString();
        } catch (DateTimeParseException notOffset) {
            try {
                return LocalDateTime.parse(text).atOffset(ZoneOffset.UTC).toString();
            } catch (DateTimeParseException unreadable) {
                return null;
            }
        }
    }
}
