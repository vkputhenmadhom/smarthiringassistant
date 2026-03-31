package org.vinod.sha.gateway.graphql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Slf4j
@Controller
public class CandidateGraphQLResolver {


    private final WebClient.Builder webClientBuilder;

    public CandidateGraphQLResolver(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    @QueryMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Map<String, Object> candidates(
            @Argument Integer page,
            @Argument Integer size,
            @Argument String status,
            @Argument String search) {
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : size;
        String uri = "http://resume-parser-service:8002/resumes?page=" + p + "&size=" + s
                + (status != null ? "&status=" + status : "")
                + (search != null ? "&search=" + search : "");
        try {
            return webClientBuilder.build().get().uri(uri).retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            log.warn("resume-parser-service unreachable: {}", e.getMessage());
            return stubCandidatePage();
        }
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER','CANDIDATE')")
    public Map<String, Object> candidate(@Argument String id) {
        try {
            return webClientBuilder.build()
                    .get().uri("http://resume-parser-service:8002/resumes/" + id)
                    .retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            return stubCandidate(id);
        }
    }

    @QueryMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    public Map<String, Object> myProfile() {
        // In a real implementation, extract candidateId from the Security Context
        return stubCandidate("me");
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Map<String, Object> matchesForJob(@Argument String jobId,
                                              @Argument Integer page,
                                              @Argument Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : size;
        try {
            return webClientBuilder.build()
                    .get().uri("http://candidate-matcher-service:8003/matches/job/" + jobId + "?page=" + p + "&size=" + s)
                    .retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            return emptyPage();
        }
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> matchesForCandidate(@Argument String candidateId,
                                                    @Argument Integer page,
                                                    @Argument Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : size;
        try {
            return webClientBuilder.build()
                    .get().uri("http://candidate-matcher-service:8003/matches/candidate/" + candidateId + "?page=" + p + "&size=" + s)
                    .retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            return emptyPage();
        }
    }

    // ── Mutations ────────────────────────────────────────────────────────────

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> triggerMatching(@Argument String candidateId,
                                                @Argument String jobId) {
        Map<String, Object> body = Map.of("candidateId", candidateId, "jobId", jobId);
        try {
            return webClientBuilder.build()
                    .post().uri("http://candidate-matcher-service:8003/matches")
                    .bodyValue(body)
                    .retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            return stubMatch(candidateId, jobId);
        }
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Map<String, Object> updateMatchStatus(@Argument String matchId,
                                                  @Argument String status) {
        Map<String, Object> body = Map.of("status", status);
        try {
            return webClientBuilder.build()
                    .put().uri("http://candidate-matcher-service:8003/matches/" + matchId + "/status")
                    .bodyValue(body)
                    .retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            return stubMatch(matchId, "");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> emptyPage() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("content", List.of());
        m.put("totalElements", 0);
        m.put("totalPages", 0);
        m.put("page", 0);
        m.put("size", 20);
        return m;
    }

    private Map<String, Object> stubCandidatePage() {
        Map<String, Object> m = emptyPage();
        m.put("content", List.of(stubCandidate("1"), stubCandidate("2")));
        m.put("totalElements", 2);
        m.put("totalPages", 1);
        return m;
    }

    private Map<String, Object> stubCandidate(String id) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", id);
        c.put("userId", id);
        c.put("name", "Jane Doe");
        c.put("email", "jane.doe@example.com");
        c.put("skills", List.of("Java", "Spring Boot", "React"));
        c.put("experience", List.of());
        c.put("education", List.of());
        c.put("parseStatus", "COMPLETED");
        c.put("screeningStatus", "NOT_STARTED");
        c.put("matchScore", 0.0);
        return c;
    }

    private Map<String, Object> stubMatch(String candidateId, String jobId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", UUID.randomUUID().toString());
        m.put("candidateId", candidateId);
        m.put("jobId", jobId);
        m.put("score", 0.75);
        m.put("status", "PENDING");
        m.put("skillMatches", List.of("Java", "Spring Boot"));
        m.put("skillGaps", List.of("Kubernetes"));
        return m;
    }
}

