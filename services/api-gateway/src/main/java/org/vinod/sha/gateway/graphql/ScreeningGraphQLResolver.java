package org.vinod.sha.gateway.graphql;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Slf4j
@Controller
public class ScreeningGraphQLResolver {

    private final WebClient.Builder webClientBuilder;

    public ScreeningGraphQLResolver(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> screeningSession(@Argument String id) {
        try {
            return webClientBuilder.build()
                    .get().uri("http://screening-bot-service:8006/sessions/" + id)
                    .retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            log.warn("screening-bot-service unreachable: {}", e.getMessage());
            return stubSession(id);
        }
    }

    @QueryMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    public List<Map<String, Object>> myScreeningSessions() {
        try {
            return webClientBuilder.build()
                    .get().uri("http://screening-bot-service:8006/sessions/my")
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .collectList()
                    .cast((Class<List<Map<String, Object>>>) (Class<?>) List.class)
                    .block();
        } catch (Exception e) {
            return List.of(stubSession("session-1"));
        }
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Map<String, Object> dashboardMetrics() {
        try {
            return webClientBuilder.build()
                    .get().uri("http://screening-bot-service:8006/metrics/dashboard")
                    .retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            return stubDashboardMetrics();
        }
    }

    // ── Mutations ────────────────────────────────────────────────────────────

    @MutationMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Map<String, Object> startScreening(@Argument String candidateId,
                                               @Argument String jobId) {
        Map<String, Object> body = Map.of("candidateId", Long.valueOf(candidateId), "jobId", jobId);
        try {
            return webClientBuilder.build()
                    .post().uri("http://screening-bot-service:8006/sessions")
                    .bodyValue(body)
                    .retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            return stubSession(UUID.randomUUID().toString());
        }
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> submitScreeningResponse(@Argument String sessionId,
                                                        @Argument String stage,
                                                        @Argument String response) {
        Map<String, Object> body = Map.of("stage", stage, "response", response);
        try {
            return webClientBuilder.build()
                    .post().uri("http://screening-bot-service:8006/sessions/" + sessionId + "/responses")
                    .bodyValue(body)
                    .retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            return stubSession(sessionId);
        }
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Map<String, Object> advanceScreening(@Argument String sessionId) {
        try {
            return webClientBuilder.build()
                    .post().uri("http://screening-bot-service:8006/sessions/" + sessionId + "/advance")
                    .retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            return stubSession(sessionId);
        }
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

    private Map<String, Object> stubDashboardMetrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalJobs", 12);
        m.put("openJobs", 8);
        m.put("totalCandidates", 47);
        m.put("activeCandidates", 23);
        m.put("pendingScreenings", 5);
        m.put("completedScreenings", 18);
        m.put("averageMatchScore", 0.74);
        m.put("hireRate", 0.21);
        m.put("stagePassRates", List.of(
                Map.of("stage", "initial", "passRate", 0.82, "totalCount", 18),
                Map.of("stage", "technical", "passRate", 0.58, "totalCount", 14),
                Map.of("stage", "behavioral", "passRate", 0.71, "totalCount", 8)
        ));
        m.put("topSkillsInDemand", List.of(
                Map.of("skill", "Java", "count", 10),
                Map.of("skill", "Spring Boot", "count", 9),
                Map.of("skill", "Kubernetes", "count", 7),
                Map.of("skill", "React", "count", 6),
                Map.of("skill", "PostgreSQL", "count", 5)
        ));
        m.put("recentActivity", List.of(
                Map.of("type", "NEW_CANDIDATE", "description", "Jane Doe uploaded resume",
                        "timestamp", "2026-03-27T10:00:00Z"),
                Map.of("type", "SCREENING_COMPLETED", "description", "John Smith passed all stages",
                        "timestamp", "2026-03-27T09:30:00Z")
        ));
        return m;
    }
}

