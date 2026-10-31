package org.vinod.sha.gateway.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.graphql.execution.ErrorType;
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
    private static final String MATCHER_SERVICE_BASE = "http://candidate-matcher-service:8003/api/matches";
    private static final String SCREENING_BACKEND = "screening-bot-service";
    private static final String JOB_BACKEND = "job-analyzer-service";
    private static final String MATCHER_BACKEND = "candidate-matcher-service";
    private static final String RESUME_SERVICE_BASE = "http://resume-parser-service:8002/api/resumes";
    private static final String RESUME_BACKEND = "resume-parser-service";
    /** Items in the HR dashboard's recent activity, and the newest resumes read for it */
    private static final int RECENT_ACTIVITY = 20;

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

    /** HR can open any session; a candidate only their own. */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<Map<String, Object>> screeningSession(@Argument String id) {
        return requestMap(SCREENING_SERVICE_BASE + "/sessions/" + id)
                .onErrorMap(e -> BackendErrors.read("screening service", "screening " + id, e))
                .zipWith(Caller.current())
                .flatMap(sessionAndCaller -> sessionAndCaller.getT2().mayRead(sessionAndCaller.getT1().get("candidateId"))
                        ? Mono.just(sessionAndCaller.getT1())
                        : Mono.error(new ClientFacingException(ErrorType.FORBIDDEN, "You can only see your own screenings.")));
    }

    /** HR gets every session; a candidate gets their own (screening-bot filters /sessions/my by the forwarded JWT). */
    @QueryMapping
    @PreAuthorize("hasAnyRole('CANDIDATE','HR_ADMIN','RECRUITER')")
    public Mono<List<Map<String, Object>>> myScreeningSessions(
            @ContextValue(name = "Authorization", required = false) String authorization) {
        return Caller.current().flatMap(caller -> caller.hr()
                        ? requestList(SCREENING_SERVICE_BASE + "/sessions", SCREENING_BACKEND)
                        : gatewayResilience.protect(SCREENING_BACKEND,
                                webClientBuilder.build()
                                        .get().uri(SCREENING_SERVICE_BASE + "/sessions/my")
                                        .header("Authorization", authorization != null ? authorization : "")
                                        .retrieve()
                                        .bodyToFlux(Map.class)
                                        .map(this::toStringObjectMap)
                                        .collectList()))
                .onErrorMap(e -> BackendErrors.read("screening service", "the screening sessions", e));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Mono<Map<String, Object>> dashboardMetrics(
            @ContextValue(name = "Authorization", required = false) String authorization) {
        // A backend that is down fails the dashboard with a message naming it, rather than showing zeros.
        Mono<Map<String, Object>> jobMetricsMono = requestMap(JOB_SERVICE_BASE + "/metrics/dashboard", JOB_BACKEND)
                .onErrorMap(e -> BackendErrors.read("job service", "the dashboard's job numbers", e));
        Mono<Map<String, Object>> screeningMetricsMono = requestMap(SCREENING_SERVICE_BASE + "/metrics/dashboard", SCREENING_BACKEND)
                .onErrorMap(e -> BackendErrors.read("screening service", "the dashboard's screening numbers", e));
        // Candidates are the people with a resume, as on the Candidates page (resume-parser checks the HR JWT).
        Mono<Map<String, Object>> candidatesMono = gatewayResilience.protect(RESUME_BACKEND,
                        webClientBuilder.build()
                                .get().uri(RESUME_SERVICE_BASE + "/candidates?page=0&size=" + RECENT_ACTIVITY)
                                .header("Authorization", authorization != null ? authorization : "")
                                .retrieve().bodyToMono(Map.class))
                .map(this::toStringObjectMap)
                .onErrorMap(e -> BackendErrors.read("resume service", "the dashboard's candidate numbers", e));
        Mono<List<Map<String, Object>>> sessionsMono = requestList(SCREENING_SERVICE_BASE + "/sessions", SCREENING_BACKEND)
                .onErrorMap(e -> BackendErrors.read("screening service", "the recent applications", e));

        return Mono.zip(jobMetricsMono, screeningMetricsMono, candidatesMono, sessionsMono)
                .flatMap(t -> jobTitles(t.getT4()).map(titles -> {
                    Map<String, Object> metrics = mergeDashboardMetrics(t.getT1(), t.getT2());
                    metrics.put("totalCandidates", intValue(t.getT3().get("totalElements"), 0));
                    List<Map<String, Object>> activity = DashboardActivity.build(listOfMaps(t.getT3().get("content")),
                            t.getT4(), titles, listOfMaps(t.getT1().get("recentActivity")));
                    metrics.put("recentActivity", sortedActivity(activity));
                    return metrics;
                }));
    }

    /** Titles of the jobs in the most recent sessions; a job that cannot be read is simply left without a title. */
    private Mono<Map<String, String>> jobTitles(List<Map<String, Object>> sessions) {
        List<String> jobIds = sessions.stream()
                .sorted(Comparator.comparing((Map<String, Object> s) -> String.valueOf(s.getOrDefault("createdAt", ""))).reversed())
                .map(s -> String.valueOf(s.get("jobId")))
                .distinct()
                .limit(RECENT_ACTIVITY)
                .toList();
        return reactor.core.publisher.Flux.fromIterable(jobIds)
                .flatMap(id -> requestMap(JOB_SERVICE_BASE + "/jobs/" + id, JOB_BACKEND)
                        .map(job -> Map.entry(id, jobLabel(job)))
                        .onErrorResume(e -> Mono.empty()))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    /** "Software Engineer, Accenture" */
    private static String jobLabel(Map<String, Object> job) {
        String title = Objects.toString(job.get("jobTitle"), "a job");
        String company = Objects.toString(job.get("companyName"), "");
        return company.isBlank() ? title : title + ", " + company;
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
                .onErrorMap(e -> BackendErrors.read("screening service", "your screenings", e));

        // 2. User's match records (applications) from candidate-matcher-service
        Mono<List<Map<String, Object>>> matchesMono = candidateId != null
                ? requestList(MATCHER_SERVICE_BASE + "/candidate/" + candidateId + "/matches", MATCHER_BACKEND)
                        .onErrorMap(e -> BackendErrors.read("matching service", "your applications", e))
                : Mono.just(List.of());

        // 3. Open jobs count in the job market candidates see (job-analyzer JOB_MARKET)
        Mono<Integer> openJobsMono = requestMap(JOB_SERVICE_BASE + "/metrics/dashboard?marketOnly=true", JOB_BACKEND)
                .map(m -> intValue(m.get("openJobs"), 0))
                .onErrorMap(e -> BackendErrors.read("job service", "the open jobs count", e));

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
                });
    }

    // ── Mutations ────────────────────────────────────────────────────────────

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<Map<String, Object>> startScreening(@Argument String candidateId,
                                                     @Argument String jobId) {
        Map<String, Object> body = Map.of("candidateId", Long.valueOf(candidateId), "jobId", jobId);
        return gatewayResilience.protectWrite(SCREENING_BACKEND,
                        webClientBuilder.build()
                                .post().uri(SCREENING_SERVICE_BASE + "/sessions")
                                .bodyValue(body)
                                .retrieve().bodyToMono(Map.class))
                .map(this::toStringObjectMap)
                .onErrorMap(e -> screeningWriteError("start", "the screening", e));
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<Map<String, Object>> submitScreeningResponse(@Argument String sessionId,
                                                              @Argument String stage,
                                                              @Argument String response) {
        Map<String, Object> body = Map.of("stage", stage, "response", response);
        return gatewayResilience.protectWrite(SCREENING_BACKEND,
                        webClientBuilder.build()
                                .post().uri(SCREENING_SERVICE_BASE + "/sessions/" + sessionId + "/responses")
                                .bodyValue(body)
                                .retrieve().bodyToMono(Map.class))
                .map(this::toStringObjectMap)
                .onErrorMap(e -> screeningWriteError("submit your answer for", "screening " + sessionId, e));
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Mono<Map<String, Object>> advanceScreening(@Argument String sessionId) {
        return gatewayResilience.protectWrite(SCREENING_BACKEND,
                        webClientBuilder.build()
                                .post().uri(SCREENING_SERVICE_BASE + "/sessions/" + sessionId + "/advance")
                                .retrieve().bodyToMono(Map.class))
                .map(this::toStringObjectMap)
                .onErrorMap(e -> screeningWriteError("advance", "screening " + sessionId, e));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static ClientFacingException screeningWriteError(String action, String target, Throwable error) {
        return BackendErrors.write("screening service", action, target, "Reload the screening", error);
    }

    /** Extract numeric user ID from a Bearer JWT already verified by GraphQLAuthorizationInterceptor. */
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

        return metrics;
    }

    private List<Map<String, Object>> sortedActivity(List<Map<String, Object>> activity) {
        List<Map<String, Object>> normalized = new ArrayList<>(activity.stream().map(this::normalizeActivityTimestamp).toList());
        normalized.sort((a, b) -> String.valueOf(b.getOrDefault("timestamp", "")).compareTo(String.valueOf(a.getOrDefault("timestamp", ""))));
        return normalized.stream().limit(RECENT_ACTIVITY).toList();
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

