package org.vinod.sha.gateway.graphql;

import graphql.ErrorClassification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.client.WebClient;
import org.vinod.sha.gateway.resilience.GatewayResilience;
import reactor.core.publisher.Mono;

import java.util.*;

@Slf4j
@Controller
public class CandidateGraphQLResolver {

    private static final String RESUME_PARSER_BACKEND = "resume-parser-service";
    private static final String MATCHER_BACKEND = "candidate-matcher-service";
    static final ErrorClassification NOT_SUPPORTED = ErrorClassification.errorClassification("NOT_SUPPORTED");


    private final WebClient.Builder webClientBuilder;
    private final GatewayResilience gatewayResilience;

    public CandidateGraphQLResolver(WebClient.Builder webClientBuilder,
                                    GatewayResilience gatewayResilience) {
        this.webClientBuilder = webClientBuilder;
        this.gatewayResilience = gatewayResilience;
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
            return gatewayResilience.protect(RESUME_PARSER_BACKEND,
                            webClientBuilder.build().get().uri(uri).retrieve().bodyToMono(Map.class).map(this::toStringObjectMap))
                    .block();
        } catch (Exception e) {
            log.warn("resume-parser-service unreachable: {}", e.getMessage());
            return stubCandidatePage();
        }
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER','CANDIDATE')")
    public Map<String, Object> candidate(@Argument String id) {
        try {
            return gatewayResilience.protect(RESUME_PARSER_BACKEND,
                            webClientBuilder.build()
                                    .get().uri("http://resume-parser-service:8002/resumes/" + id)
                                    .retrieve().bodyToMono(Map.class)
                                    .map(this::toStringObjectMap))
                    .block();
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
            return gatewayResilience.protect(MATCHER_BACKEND,
                            webClientBuilder.build()
                                    .get().uri("http://candidate-matcher-service:8003/matches/job/" + jobId + "?page=" + p + "&size=" + s)
                                    .retrieve().bodyToMono(Map.class)
                                    .map(this::toStringObjectMap))
                    .block();
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
            return gatewayResilience.protect(MATCHER_BACKEND,
                            webClientBuilder.build()
                                    .get().uri("http://candidate-matcher-service:8003/matches/candidate/" + candidateId + "?page=" + p + "&size=" + s)
                                    .retrieve().bodyToMono(Map.class)
                                    .map(this::toStringObjectMap))
                    .block();
        } catch (Exception e) {
            return emptyPage();
        }
    }

    // ── Mutations ────────────────────────────────────────────────────────────

    /**
     * Not wired to candidate-matcher yet: the matcher needs the candidate's parsed resume (skills, experience,
     * location) to score a pair, which this mutation does not have. Matching runs automatically when a resume
     * is parsed. This used to answer with a made-up 75% match.
     */
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<Map<String, Object>> triggerMatching(@Argument String candidateId,
                                                     @Argument String jobId) {
        return Mono.error(new ClientFacingException(NOT_SUPPORTED,
                "Matching a single candidate to a job on demand is not supported yet. "
                        + "Candidates are matched to every open job automatically when their resume is parsed."));
    }

    /**
     * Not wired to candidate-matcher yet: the matcher's statuses (NEW, REVIEWED, ACCEPTED, REJECTED) do not
     * line up with the GraphQL MatchStatus (PENDING, MATCHED, SHORTLISTED, REJECTED, HIRED). This used to
     * answer with a stub match, so the change looked saved when it was not.
     */
    @MutationMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Mono<Map<String, Object>> updateMatchStatus(@Argument String matchId,
                                                       @Argument String status) {
        return Mono.error(new ClientFacingException(NOT_SUPPORTED,
                "Changing a match's status is not supported yet, so match " + matchId + " was not changed."));
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


    @SuppressWarnings("unchecked")
    private Map<String, Object> toStringObjectMap(Map<?, ?> raw) {
        return raw == null ? Map.of() : (Map<String, Object>) raw;
    }
}

