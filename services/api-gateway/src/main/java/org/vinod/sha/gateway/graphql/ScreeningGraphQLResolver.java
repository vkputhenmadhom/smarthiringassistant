package org.vinod.sha.gateway.graphql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Controller
public class ScreeningGraphQLResolver {

    private static final String SCREENING_SERVICE_BASE = "http://screening-bot-service:8006/api/screening";
    private static final String JOB_SERVICE_BASE = "http://job-analyzer-service:8005/api/jobs";

    private final WebClient.Builder webClientBuilder;

    public ScreeningGraphQLResolver(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<Map<String, Object>> screeningSession(@Argument String id) {
        return requestMap(SCREENING_SERVICE_BASE + "/sessions/" + id)
                .onErrorResume(e -> {
                    log.warn("screening-bot-service unreachable: {}", e.getMessage());
                    return Mono.just(stubSession(id));
                });
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('CANDIDATE','HR_ADMIN','RECRUITER')")
    public Mono<List<Map<String, Object>>> myScreeningSessions() {
        return webClientBuilder.build()
                .get().uri(SCREENING_SERVICE_BASE + "/sessions")
                .retrieve()
                .bodyToFlux(Map.class)
                .map(this::toStringObjectMap)
                .collectList()
                .onErrorResume(e -> {
                    log.warn("screening-bot-service sessions unavailable: {}", e.getMessage());
                    return Mono.just(List.of(stubSession("session-1")));
                });
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Mono<Map<String, Object>> dashboardMetrics() {
        Mono<Map<String, Object>> jobMetricsMono = requestMap(JOB_SERVICE_BASE + "/metrics/dashboard")
                .onErrorReturn(Map.of());
        Mono<Map<String, Object>> screeningMetricsMono = requestMap(SCREENING_SERVICE_BASE + "/metrics/dashboard")
                .onErrorReturn(Map.of());

        return Mono.zip(jobMetricsMono, screeningMetricsMono)
                .map(tuple -> mergeDashboardMetrics(tuple.getT1(), tuple.getT2()))
                .onErrorResume(e -> {
                    log.warn("dashboard metrics aggregation failed: {}", e.getMessage());
                    return Mono.just(emptyDashboardMetrics());
                });
    }

    // ── Mutations ────────────────────────────────────────────────────────────

    @MutationMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Mono<Map<String, Object>> startScreening(@Argument String candidateId,
                                                     @Argument String jobId) {
        Map<String, Object> body = Map.of("candidateId", Long.valueOf(candidateId), "jobId", jobId);
        return webClientBuilder.build()
                .post().uri(SCREENING_SERVICE_BASE + "/sessions")
                .bodyValue(body)
                .retrieve().bodyToMono(Map.class)
                .map(this::toStringObjectMap)
                .onErrorResume(e -> Mono.just(stubSession(UUID.randomUUID().toString())));
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<Map<String, Object>> submitScreeningResponse(@Argument String sessionId,
                                                              @Argument String stage,
                                                              @Argument String response) {
        Map<String, Object> body = Map.of("stage", stage, "response", response);
        return webClientBuilder.build()
                .post().uri(SCREENING_SERVICE_BASE + "/sessions/" + sessionId + "/responses")
                .bodyValue(body)
                .retrieve().bodyToMono(Map.class)
                .map(this::toStringObjectMap)
                .onErrorResume(e -> Mono.just(stubSession(sessionId)));
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Mono<Map<String, Object>> advanceScreening(@Argument String sessionId) {
        return webClientBuilder.build()
                .post().uri(SCREENING_SERVICE_BASE + "/sessions/" + sessionId + "/advance")
                .retrieve().bodyToMono(Map.class)
                .map(this::toStringObjectMap)
                .onErrorResume(e -> Mono.just(stubSession(sessionId)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> stubSession(String id) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", id);
        s.put("candidateId", "1");
        s.put("jobId", "job-1");
        s.put("currentStage", "initial");
        s.put("status", "IN_PROGRESS");
        s.put("decision", "PENDING");
        s.put("finalScore", 0.0);
        s.put("stageResults", List.of());
        s.put("failureReasons", List.of());
        return s;
    }

    private Mono<Map<String, Object>> requestMap(String uri) {
        return webClientBuilder.build()
                .get().uri(uri)
                .retrieve().bodyToMono(Map.class)
                .map(this::toStringObjectMap);
    }

    private Map<String, Object> mergeDashboardMetrics(Map<String, Object> jobs, Map<String, Object> screening) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalJobs", intValue(jobs.get("totalJobs"), 0));
        metrics.put("openJobs", intValue(jobs.get("openJobs"), 0));
        metrics.put("totalCandidates", intValue(screening.get("totalCandidates"), 0));
        metrics.put("activeCandidates", intValue(screening.get("activeCandidates"), 0));
        metrics.put("pendingScreenings", intValue(screening.get("pendingScreenings"), 0));
        metrics.put("completedScreenings", intValue(screening.get("completedScreenings"), 0));
        metrics.put("averageMatchScore", doubleValue(screening.get("averageMatchScore"), 0.0));
        metrics.put("hireRate", doubleValue(screening.get("hireRate"), 0.0));
        metrics.put("stagePassRates", listOfMaps(screening.get("stagePassRates")));
        metrics.put("topSkillsInDemand", listOfMaps(jobs.get("topSkillsInDemand")));

        List<Map<String, Object>> recentActivity = new ArrayList<>();
        recentActivity.addAll(listOfMaps(jobs.get("recentActivity")));
        recentActivity.addAll(listOfMaps(screening.get("recentActivity")));
        recentActivity = new ArrayList<>(recentActivity.stream().map(this::normalizeActivityTimestamp).toList());
        recentActivity.sort((a, b) -> String.valueOf(b.getOrDefault("timestamp", "")).compareTo(String.valueOf(a.getOrDefault("timestamp", ""))));
        metrics.put("recentActivity", recentActivity.stream().limit(20).toList());
        return metrics;
    }

    private Map<String, Object> normalizeActivityTimestamp(Map<String, Object> activity) {
        Map<String, Object> normalized = new LinkedHashMap<>(activity);
        Object ts = activity.get("timestamp");
        normalized.put("timestamp", normalizeDateTime(ts));
        return normalized;
    }

    private String normalizeDateTime(Object value) {
        if (value == null) {
            return OffsetDateTime.now(ZoneOffset.UTC).toString();
        }
        String raw = String.valueOf(value);
        if (raw.isBlank()) {
            return OffsetDateTime.now(ZoneOffset.UTC).toString();
        }
        try {
            return OffsetDateTime.parse(raw).toString();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(raw).atOffset(ZoneOffset.UTC).toString();
            } catch (DateTimeParseException ignoredAgain) {
                return OffsetDateTime.now(ZoneOffset.UTC).toString();
            }
        }
    }

    private Map<String, Object> emptyDashboardMetrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalJobs", 0);
        m.put("openJobs", 0);
        m.put("totalCandidates", 0);
        m.put("activeCandidates", 0);
        m.put("pendingScreenings", 0);
        m.put("completedScreenings", 0);
        m.put("averageMatchScore", 0.0);
        m.put("hireRate", 0.0);
        m.put("stagePassRates", List.of());
        m.put("topSkillsInDemand", List.of());
        m.put("recentActivity", List.of());
        return m;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double doubleValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toStringObjectMap(Map<?, ?> raw) {
        return raw == null ? Map.of() : (Map<String, Object>) raw;
    }
}

