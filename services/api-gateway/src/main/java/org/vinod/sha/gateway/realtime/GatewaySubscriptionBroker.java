package org.vinod.sha.gateway.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.vinod.shared.config.RabbitMQConfiguration;
import org.vinod.shared.events.CandidateMatchedEvent;
import org.vinod.shared.events.ScreeningCompletedEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class GatewaySubscriptionBroker {

    private final ConcurrentMap<String, Sinks.Many<Map<String, Object>>> screeningSinks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Sinks.Many<Map<String, Object>>> matchSinks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Sinks.Many<Map<String, Object>>> notificationSinks = new ConcurrentHashMap<>();

    public Flux<Map<String, Object>> screeningUpdates(String sessionId) {
        return sinkFor(screeningSinks, sessionId).asFlux();
    }

    public Flux<Map<String, Object>> matchUpdates(String jobId) {
        return sinkFor(matchSinks, jobId).asFlux();
    }

    public Flux<Map<String, Object>> notificationUpdates(String userId) {
        return sinkFor(notificationSinks, userId).asFlux();
    }

    public void publishScreeningUpdate(String sessionId, Map<String, Object> payload) {
        emit(screeningSinks, sessionId, payload);
    }

    public void publishMatchUpdate(String jobId, Map<String, Object> payload) {
        emit(matchSinks, jobId, payload);
    }

    public void publishNotification(String userId, Map<String, Object> payload) {
        emit(notificationSinks, userId, payload);
    }

    private Sinks.Many<Map<String, Object>> sinkFor(ConcurrentMap<String, Sinks.Many<Map<String, Object>>> registry,
                                                    String key) {
        return registry.computeIfAbsent(key, ignored -> Sinks.many().multicast().onBackpressureBuffer());
    }

    private void emit(ConcurrentMap<String, Sinks.Many<Map<String, Object>>> registry,
                      String key,
                      Map<String, Object> payload) {
        Sinks.EmitResult result = sinkFor(registry, key).tryEmitNext(payload);
        if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            log.debug("Realtime emit dropped for key {} with result {}", key, result);
        }
    }
}

@Controller
class SubscriptionGraphQLResolver {

    private final GatewaySubscriptionBroker subscriptionBroker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    SubscriptionGraphQLResolver(GatewaySubscriptionBroker subscriptionBroker) {
        this.subscriptionBroker = subscriptionBroker;
    }

    @SubscriptionMapping
    @PreAuthorize("isAuthenticated()")
    public Flux<Map<String, Object>> screeningUpdated(@Argument String sessionId) {
        return subscriptionBroker.screeningUpdates(sessionId);
    }

    @SubscriptionMapping
    @PreAuthorize("isAuthenticated()")
    public Flux<Map<String, Object>> newMatchForJob(@Argument String jobId) {
        return subscriptionBroker.matchUpdates(jobId);
    }

    @SubscriptionMapping
    @PreAuthorize("isAuthenticated()")
    public Flux<Map<String, Object>> newNotification(@Argument String userId,
                                                     @ContextValue(name = "Authorization", required = false) String authorization) {
        String authenticatedUserId = extractAuthenticatedUserId(authorization)
                .orElseThrow(() -> new AccessDeniedException("Notification subscription requires a valid authenticated user."));
        if (!authenticatedUserId.equals(userId)) {
            throw new AccessDeniedException("You can only subscribe to your own notifications.");
        }
        return subscriptionBroker.notificationUpdates(userId);
    }

    private Optional<String> extractAuthenticatedUserId(String authorization) {
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
            Object id = claims.get("id");
            return id == null ? Optional.empty() : Optional.of(String.valueOf(id));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}

@Configuration
class GatewaySubscriptionQueueConfig {

    static final String GATEWAY_CANDIDATE_MATCHED_QUEUE = "gateway.candidate.matched.queue";
    static final String GATEWAY_SCREENING_COMPLETED_QUEUE = "gateway.screening.completed.queue";

    @Bean
    Queue gatewayCandidateMatchedQueue() {
        return QueueBuilder.durable(GATEWAY_CANDIDATE_MATCHED_QUEUE).build();
    }

    @Bean
    TopicExchange gatewayMatcherExchange() {
        return new TopicExchange(RabbitMQConfiguration.MATCHER_EXCHANGE, true, false);
    }

    @Bean
    Binding gatewayCandidateMatchedBinding(@Qualifier("gatewayCandidateMatchedQueue") Queue gatewayCandidateMatchedQueue,
                                           @Qualifier("gatewayMatcherExchange") TopicExchange gatewayMatcherExchange) {
        return BindingBuilder.bind(gatewayCandidateMatchedQueue)
                .to(gatewayMatcherExchange)
                .with(RabbitMQConfiguration.CANDIDATE_MATCHED_ROUTING_KEY);
    }

    @Bean
    Queue gatewayScreeningCompletedQueue() {
        return QueueBuilder.durable(GATEWAY_SCREENING_COMPLETED_QUEUE).build();
    }

    @Bean
    TopicExchange gatewayScreeningExchange() {
        return new TopicExchange(RabbitMQConfiguration.SCREENING_EXCHANGE, true, false);
    }

    @Bean
    Binding gatewayScreeningCompletedBinding(@Qualifier("gatewayScreeningCompletedQueue") Queue gatewayScreeningCompletedQueue,
                                             @Qualifier("gatewayScreeningExchange") TopicExchange gatewayScreeningExchange) {
        return BindingBuilder.bind(gatewayScreeningCompletedQueue)
                .to(gatewayScreeningExchange)
                .with(RabbitMQConfiguration.SCREENING_COMPLETED_ROUTING_KEY);
    }
}

@Slf4j
@Component
class GatewayRabbitSubscriptionListener {

    private final GatewaySubscriptionBroker subscriptionBroker;

    GatewayRabbitSubscriptionListener(GatewaySubscriptionBroker subscriptionBroker) {
        this.subscriptionBroker = subscriptionBroker;
    }

    @RabbitListener(queues = "#{@gatewayCandidateMatchedQueue.name}")
    void onCandidateMatched(CandidateMatchedEvent event) {
        if (event == null || event.getJobId() == null || event.getCandidateId() == null) {
            return;
        }

        Map<String, Object> matchPayload = toMatchPayload(event);
        subscriptionBroker.publishMatchUpdate(event.getJobId(), matchPayload);

        String userId = String.valueOf(event.getCandidateId());
        // MVP assumption: candidateId is used as the subscription userId until a dedicated user mapping is added.
        subscriptionBroker.publishNotification(userId, toNotificationPayload(
                userId,
                "MATCH",
                "New match found",
                "A new match was generated for your profile."
        ));
    }

    @RabbitListener(queues = "#{@gatewayScreeningCompletedQueue.name}")
    void onScreeningCompleted(ScreeningCompletedEvent event) {
        if (event == null || event.getSessionId() == null || event.getCandidateId() == null) {
            return;
        }

        Map<String, Object> screeningPayload = toScreeningPayload(event);
        subscriptionBroker.publishScreeningUpdate(event.getSessionId(), screeningPayload);

        String userId = String.valueOf(event.getCandidateId());
        subscriptionBroker.publishNotification(userId, toNotificationPayload(
                userId,
                "SCREENING",
                "Screening completed",
                "Your screening workflow has completed with decision: " + screeningPayload.get("decision")
        ));
    }

    private Map<String, Object> toMatchPayload(CandidateMatchedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", valueOrUuid(event.getMatchId()));
        payload.put("candidateId", String.valueOf(event.getCandidateId()));
        payload.put("jobId", event.getJobId());
        payload.put("score", normalizeScore(event.getMatchScore()));
        payload.put("status", toGraphqlMatchStatus(event.getStatus()));
        payload.put("skillMatches", List.of());
        payload.put("skillGaps", List.of());
        payload.put("matchedAt", toIsoTimestamp(event.getMatchedAt()));
        return payload;
    }

    private Map<String, Object> toScreeningPayload(ScreeningCompletedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", event.getSessionId());
        payload.put("candidateId", String.valueOf(event.getCandidateId()));
        payload.put("jobId", event.getJobId());
        payload.put("currentStage", "final");
        payload.put("status", "COMPLETED");
        payload.put("decision", event.getDecision() == null ? "PENDING" : event.getDecision());
        payload.put("finalScore", event.getFinalScore() == null ? 0.0 : event.getFinalScore());
        payload.put("stageResults", List.of());
        payload.put("failureReasons", event.getFailureReasons() == null ? List.of() : event.getFailureReasons());
        payload.put("createdAt", toIsoTimestamp(event.getCompletedAt()));
        payload.put("completedAt", toIsoTimestamp(event.getCompletedAt()));
        return payload;
    }

    private Map<String, Object> toNotificationPayload(String userId,
                                                      String type,
                                                      String title,
                                                      String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", UUID.randomUUID().toString());
        payload.put("userId", userId);
        payload.put("type", type);
        payload.put("title", title);
        payload.put("message", message);
        payload.put("read", false);
        payload.put("createdAt", Instant.now().toString());
        return payload;
    }

    private String valueOrUuid(String value) {
        return (value == null || value.isBlank()) ? UUID.randomUUID().toString() : value;
    }

    private double normalizeScore(Double rawScore) {
        if (rawScore == null) {
            return 0.0;
        }
        double normalized = rawScore > 1.0 ? rawScore / 100.0 : rawScore;
        return Math.max(0.0, Math.min(1.0, normalized));
    }

    private String toGraphqlMatchStatus(String status) {
        if (status == null || status.isBlank()) {
            return "PENDING";
        }
        return switch (status.toUpperCase()) {
            case "MATCHED" -> "MATCHED";
            case "REJECTED" -> "REJECTED";
            case "ACCEPTED" -> "SHORTLISTED";
            case "HIRED" -> "HIRED";
            default -> "PENDING";
        };
    }

    private String toIsoTimestamp(long epoch) {
        long epochMillis = epoch <= 0
                ? Instant.now().toEpochMilli()
                : (epoch < 1_000_000_000_000L ? epoch * 1000 : epoch);
        return Instant.ofEpochMilli(epochMillis).toString();
    }
}
