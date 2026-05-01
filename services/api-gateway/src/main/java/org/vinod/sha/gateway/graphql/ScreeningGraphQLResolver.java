package org.vinod.sha.gateway.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.client.WebClient;
import org.vinod.sha.gateway.resilience.GatewayResilience;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
public class ScreeningGraphQLResolver {

    private static final String SCREENING_SERVICE_BASE = "http://screening-bot-service:8006/api/screening";
    private static final String JOB_SERVICE_BASE = "http://job-analyzer-service:8005/api/jobs";
    private static final String MATCHER_SERVICE_BASE = "http://candidate-matcher-service:8003";
    private static final String SCREENING_BACKEND = "screening-bot-service";
    private static final String JOB_BACKEND = "job-analyzer-service";
    private static final String MATCHER_BACKEND = "candidate-matcher-service";

    private final WebClient.Builder webClientBuilder;
    private final GatewayResilience gatewayResilience;
    private final ObjectMapper objectMapper;

    public ScreeningGraphQLResolver(WebClient.Builder webClientBuilder,
                                    GatewayResilience gatewayResilience) {
        this.webClientBuilder = webClientBuilder;
        this.gatewayResilience = gatewayResilience;
        this.objectMapper = new ObjectMapper();
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
        return requestList(SCREENING_SERVICE_BASE + "/sessions", SCREENING_BACKEND)
                .onErrorResume(e -> {
                    log.warn("screening-bot-service sessions unavailable: {}", e.getMessage());
                    return Mono.just(List.of(stubSession("session-1")));
                });
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Mono<Map<String, Object>> dashboardMetrics() {
        Mono<Map<String, Object>> jobMetricsMono = requestMap(JOB_SERVICE_BASE + "/metrics/dashboard", JOB_BACKEND)
                .onErrorReturn(Map.of());
        Mono<Map<String, Object>> screeningMetricsMono = requestMap(SCREENING_SERVICE_BASE + "/metrics/dashboard", SCREENING_BACKEND)
                .onErrorReturn(Map.of());

        return Mono.zip(jobMetricsMono, screeningMetricsMono)
                .map(tuple -> mergeDashboardMetrics(tuple.getT1(), tuple.getT2()))
                .onErrorResume(e -> {
                    log.warn("dashboard metrics aggregation failed: {}", e.getMessage());
                    return Mono.just(emptyDashboardMetrics());
                });
    }

    /**
     * Candidate-scoped dashboard: returns metrics specific to the logged-in user.
     * - openJobs: total open jobs (global – same for everyone)
     * - activeCandidates: this user's application (match) count
     * - pendingScreenings: this user's in-progress screening sessions
     * - averageMatchScore: this user's average match score from candidate-matcher-service
     * - recentActivity: this user's recent screening events
     */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<Map<String, Object>> myCandidateDashboard(
            @ContextValue(name = "Authorization", required = false) String authorization) {

        Long candidateId = extractUserIdFromToken(authorization);

        // 1. User's screening sessions (forward JWT so screening-bot-service filters by user)
        Mono<List<Map<String, Object>>> sessionsMono = gatewayResilience.protect(
                        SCREENING_BACKEND,
                        webClientBuilder.build()
                                .get().uri(SCREENING_SERVICE_BASE + "/sessions/my")
                                .header("Authorization", authorization != null ? authorization : "")
                                .retrieve()
                                .bodyToFlux(Map.class)
                                .map(this::toStringObjectMap)
                                .collectList())
                .onErrorReturn(List.of());

        // 2. User's match records (applications) from candidate-matcher-service
        Mono<List<Map<String, Object>>> matchesMono = candidateId != null
                ? requestList(MATCHER_SERVICE_BASE + "/candidate/" + candidateId + "/matches", MATCHER_BACKEND)
                        .onErrorReturn(List.of())
                : Mono.just(List.of());

        // 3. Open jobs count (global – same for all users)
        Mono<Integer> openJobsMono = requestMap(JOB_SERVICE_BASE + "/metrics/dashboard", JOB_BACKEND)
                .map(m -> intValue(m.get("openJobs"), 0))
                .onErrorReturn(0);

        return Mono.zip(sessionsMono, matchesMono, openJobsMono)
                .map(tuple -> {
                    List<Map<String, Object>> sessions = tuple.getT1();
                    List<Map<String, Object>> matches = tuple.getT2();
                    int openJobs = tuple.getT3();

                    long pending = sessions.stream()
                            .filter(s -> "IN_PROGRESS".equalsIgnoreCase(String.valueOf(s.getOrDefault("status", ""))))
                            .count();
                    long completed = sessions.stream()
                            .filter(s -> "COMPLETED".equalsIgnoreCase(String.valueOf(s.getOrDefault("status", ""))))
                            .count();

                    double avgMatchScore = matches.stream()
                            .map(m -> m.get("overallScore"))
                            .filter(Objects::nonNull)
                            .mapToDouble(v -> v instanceof Number n ? Math.max(0, Math.min(1, n.doubleValue() / 100.0)) : 0.0)
                            .average()
                            .orElse(0.0);

                    List<Map<String, Object>> recentActivity = sessions.stream()
                            .limit(10)
                            .map(s -> {
                                Map<String, Object> a = new LinkedHashMap<>();
                                a.put("type", "SCREENING_" + String.valueOf(s.getOrDefault("status", "UPDATED")).toUpperCase());
                                a.put("description", "Screening for job " + s.getOrDefault("jobId", "unknown")
                                        + " is " + s.getOrDefault("status", "unknown"));
                                a.put("timestamp", normalizeDateTime(s.getOrDefault("updatedAt", s.get("createdAt"))));
                                return a;
                            })
                            .collect(Collectors.toList());

                    Map<String, Object> metrics = new LinkedHashMap<>();
                    metrics.put("totalJobs", openJobs);
                    metrics.put("openJobs", openJobs);
                    metrics.put("totalCandidates", matches.size());
                    metrics.put("activeCandidates", matches.size());   // "My Applications"
                    metrics.put("pendingScreenings", (int) pending);
                    metrics.put("completedScreenings", (int) completed);
                    metrics.put("averageMatchScore", avgMatchScore);
                    metrics.put("hireRate", 0.0);
                    metrics.put("stagePassRates", List.of());
                    metrics.put("topSkillsInDemand", List.of());
                    metrics.put("recentActivity", recentActivity);
                    return metrics;
                })
                .onErrorResume(e -> {
                    log.warn("myCandidateDashboard aggregation failed for user {}: {}", candidateId, e.getMessage());
                    return Mono.just(emptyDashboardMetrics());
                });
    }

    // ── Mutations ────────────────────────────────────────────────────────────

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<Map<String, Object>> startScreening(@Argument String candidateId,
                                                     @Argument String jobId) {
        Map<String, Object> body = Map.of("candidateId", Long.valueOf(candidateId), "jobId", jobId);
        return gatewayResilience.protect(SCREENING_BACKEND,
                        webClientBuilder.build()
                                .post().uri(SCREENING_SERVICE_BASE + "/sessions")
                                .bodyValue(body)
                                .retrieve().bodyToMono(Map.class))
                .map(this::toStringObjectMap)
                .onErrorResume(e -> Mono.just(stubSession(UUID.randomUUID().toString())));
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<Map<String, Object>> submitScreeningResponse(@Argument String sessionId,
                                                              @Argument String stage,
                                                              @Argument String response) {
        Map<String, Object> body = Map.of("stage", stage, "response", response);
        return gatewayResilience.protect(SCREENING_BACKEND,
                        webClientBuilder.build()
                                .post().uri(SCREENING_SERVICE_BASE + "/sessions/" + sessionId + "/responses")
                                .bodyValue(body)
                                .retrieve().bodyToMono(Map.class))
                .map(this::toStringObjectMap)
                .onErrorResume(e -> Mono.just(stubSession(sessionId)));
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Mono<Map<String, Object>> advanceScreening(@Argument String sessionId) {
        return gatewayResilience.protect(SCREENING_BACKEND,
                        webClientBuilder.build()
                                .post().uri(SCREENING_SERVICE_BASE + "/sessions/" + sessionId + "/advance")
                                .retrieve().bodyToMono(Map.class))
                .map(this::toStringObjectMap)
                .onErrorResume(e -> Mono.just(stubSession(sessionId)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Extract numeric user ID from a Bearer JWT without a full validation library. */
    private Long extractUserIdFromToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        try {
            String[] parts = authorization.substring(7).split("\\.");
            if (parts.length < 2) return null;
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = objectMapper.readValue(
                    new String(decoded, StandardCharsets.UTF_8), Map.class);
            Object idObj = claims.get("id");
            if (idObj instanceof Number n) return n.longValue();
            if (idObj != null) return Long.parseLong(String.valueOf(idObj));
        } catch (Exception e) {
            log.debug("Failed to extract user ID from token: {}", e.getMessage());
        }
        return null;
    }

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
        return requestMap(uri, SCREENING_BACKEND);
    }

    private Mono<Map<String, Object>> requestMap(String uri, String backendName) {
        return gatewayResilience.protect(backendName,
                        webClientBuilder.build()
                                .get().uri(uri)
                                .retrieve().bodyToMono(Map.class))
                .map(this::toStringObjectMap);
    }

    private Mono<List<Map<String, Object>>> requestList(String uri, String backendName) {
        return gatewayResilience.protect(backendName,
                webClientBuilder.build()
                        .get().uri(uri)
                        .retrieve()
                        .bodyToFlux(Map.class)
                        .map(this::toStringObjectMap)
                        .collectList());
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

