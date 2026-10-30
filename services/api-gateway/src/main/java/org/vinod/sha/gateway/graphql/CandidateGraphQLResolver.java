package org.vinod.sha.gateway.graphql;

import graphql.ErrorClassification;
import graphql.GraphQLContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.vinod.sha.gateway.resilience.GatewayResilience;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.*;

@Slf4j
@Controller
public class CandidateGraphQLResolver {

    private static final String RESUME_PARSER_BACKEND = "resume-parser-service";
    private static final String MATCHER_BACKEND = "candidate-matcher-service";
    private static final String MATCHER_BASE = "http://candidate-matcher-service:8003/api/matches";
    private static final String RESUME_PARSER_BASE = "http://resume-parser-service:8002/api/resumes";
    private static final String SCREENING_BACKEND = "screening-bot-service";
    private static final String SCREENING_BASE = "http://screening-bot-service:8006/api/screening";
    /** Upper bound when the gateway must page itself (filtering by screening status). */
    private static final int ALL_CANDIDATES = 1000;
    static final ErrorClassification NOT_SUPPORTED = ErrorClassification.errorClassification("NOT_SUPPORTED");


    private final WebClient.Builder webClientBuilder;
    private final GatewayResilience gatewayResilience;

    public CandidateGraphQLResolver(WebClient.Builder webClientBuilder,
                                    GatewayResilience gatewayResilience) {
        this.webClientBuilder = webClientBuilder;
        this.gatewayResilience = gatewayResilience;
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    /**
     * Candidates are users with at least one resume; each is shown from their latest resume, with their
     * screening status and best match score. Filtering by screening status needs every candidate's status,
     * so it pages in the gateway instead of in resume-parser.
     */
    @QueryMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Mono<Map<String, Object>> candidates(@Argument Integer page,
                                                @Argument Integer size,
                                                @Argument String status,
                                                @Argument String search,
                                                @ContextValue(name = "Authorization", required = false) String authorization) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 20 : Math.max(1, size);
        boolean filter = status != null;
        String uri = UriComponentsBuilder.fromUriString(RESUME_PARSER_BASE + "/candidates")
                .queryParam("page", filter ? 0 : p)
                .queryParam("size", filter ? ALL_CANDIDATES : s)
                .queryParamIfPresent("search", Optional.ofNullable(search).filter(q -> !q.isBlank()))
                .build().encode().toUriString();
        return getMap(uri, authorization)
                .onErrorMap(e -> BackendErrors.read("resume service", "the candidates", e))
                .flatMap(resumePage -> {
                    List<Map<String, Object>> profiles = listOf(resumePage.get("content"));
                    return enrich(profiles, authorization, filter).map(candidates -> {
                        if (!filter) {
                            Map<String, Object> result = new LinkedHashMap<>(resumePage);
                            result.put("content", candidates);
                            return result;
                        }
                        List<Map<String, Object>> matching = candidates.stream()
                                .filter(c -> status.equals(Objects.toString(c.get("screeningStatus"), "NOT_STARTED")))
                                .toList();
                        int from = Math.min(matching.size(), p * s);
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("content", matching.subList(from, Math.min(matching.size(), from + s)));
                        result.put("totalElements", matching.size());
                        result.put("totalPages", (matching.size() + s - 1) / s);
                        result.put("page", p);
                        result.put("size", s);
                        return result;
                    });
                });
    }

    /** HR can open any candidate; a candidate only themselves. */
    @QueryMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER','CANDIDATE')")
    public Mono<Map<String, Object>> candidate(@Argument String id,
                                               @ContextValue(name = "Authorization", required = false) String authorization) {
        return Caller.current().flatMap(caller -> caller.mayRead(id)
                ? loadCandidate(id, authorization)
                : Mono.error(new ClientFacingException(ErrorType.FORBIDDEN, "You can only see your own profile.")));
    }

    @QueryMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    public Mono<Map<String, Object>> myProfile(@ContextValue(name = "Authorization", required = false) String authorization) {
        return Caller.current().flatMap(caller -> caller.userId() == null
                ? Mono.error(new ClientFacingException(ErrorType.UNAUTHORIZED, "Your session does not identify a user."))
                : loadCandidate(String.valueOf(caller.userId()), authorization)
                        .onErrorMap(e -> e instanceof ClientFacingException cf && cf.getClassification() == ErrorType.NOT_FOUND
                                ? new ClientFacingException(ErrorType.NOT_FOUND, "Upload a resume to create your profile.")
                                : e));
    }

    /**
     * Fills {@code CandidateMatch.candidate} for a whole list of matches with one resume-parser call (HR) or one
     * per candidate (a candidate looking at their own matches). A candidate that cannot be loaded is left null:
     * the match itself is still worth showing.
     */
    @BatchMapping(typeName = "CandidateMatch", field = "candidate")
    public Mono<Map<Map<String, Object>, Map<String, Object>>> candidate(List<Map<String, Object>> matches,
                                                                        GraphQLContext context) {
        String authorization = context.get("Authorization");
        List<String> ids = matches.stream().map(m -> String.valueOf(m.get("candidateId"))).distinct().toList();
        Mono<List<Map<String, Object>>> profiles = ids.size() == 1
                ? getMap(RESUME_PARSER_BASE + "/candidates/" + ids.get(0), authorization).map(List::of)
                : getList(RESUME_PARSER_BASE + "/candidates?userIds=" + String.join(",", ids), authorization);
        return profiles
                .onErrorResume(e -> {
                    log.warn("Could not load candidates {} for matches: {}", ids, e.toString());
                    return Mono.just(List.of());
                })
                .map(found -> {
                    Map<String, Map<String, Object>> byId = CandidateViews.byId(found, p -> p.get("userId"));
                    Map<Map<String, Object>, Map<String, Object>> result = new LinkedHashMap<>();
                    for (Map<String, Object> match : matches) {
                        Map<String, Object> profile = byId.get(String.valueOf(match.get("candidateId")));
                        if (profile != null) {
                            result.put(match, CandidateViews.candidate(profile, null, null));
                        }
                    }
                    return result;
                });
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Mono<Map<String, Object>> matchesForJob(@Argument String jobId,
                                                   @Argument Integer page,
                                                   @Argument Integer size) {
        return requestMatches(MATCHER_BASE + "/job/" + jobId + "/matches", "the matches for job " + jobId, page, size);
    }

    /** HR can read any candidate's matches; a candidate only their own. */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<Map<String, Object>> matchesForCandidate(@Argument String candidateId,
                                                         @Argument Integer page,
                                                         @Argument Integer size) {
        return Caller.current().flatMap(caller -> caller.mayRead(candidateId)
                ? requestMatches(MATCHER_BASE + "/candidate/" + candidateId + "/matches",
                        "the matches for candidate " + candidateId, page, size)
                : Mono.error(new ClientFacingException(ErrorType.FORBIDDEN, "You can only see your own matches.")));
    }

    /**
     * Not wired to candidate-matcher: the matcher needs the candidate's parsed resume (skills, experience,
     * location) to score a pair, which this mutation does not have. Matching runs automatically when a resume
     * is parsed.
     */
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<Map<String, Object>> triggerMatching(@Argument String candidateId,
                                                     @Argument String jobId) {
        return Mono.error(new ClientFacingException(NOT_SUPPORTED,
                "Matching a single candidate to a job on demand is not supported yet. "
                        + "Candidates are matched to every open job automatically when their resume is parsed."));
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Mono<Map<String, Object>> updateMatchStatus(@Argument String matchId,
                                                       @Argument String status) {
        return gatewayResilience.protectWrite(MATCHER_BACKEND,
                        webClientBuilder.build()
                                .put().uri(MATCHER_BASE + "/" + matchId)
                                .bodyValue(Map.of("status", status))
                                .retrieve().bodyToMono(Map.class))
                .map(raw -> MatchViews.match(toStringObjectMap(raw)))
                .onErrorMap(e -> BackendErrors.write("matching service", "update", "match " + matchId,
                        "Reload the matches", e));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Mono<Map<String, Object>> loadCandidate(String id, String authorization) {
        return getMap(RESUME_PARSER_BASE + "/candidates/" + id, authorization)
                .onErrorMap(e -> BackendErrors.read("resume service", "candidate " + id, e))
                .flatMap(profile -> enrich(List.of(profile), authorization, false))
                .map(candidates -> candidates.get(0));
    }

    /**
     * Adds screening status and best match score. They are secondary, so if screening-bot or the matcher is down
     * they are left null (unknown) rather than failing the read, unless the caller filters on screening status.
     */
    private Mono<List<Map<String, Object>>> enrich(List<Map<String, Object>> profiles, String authorization,
                                                   boolean screeningRequired) {
        if (profiles.isEmpty()) {
            return Mono.just(List.of());
        }
        List<String> ids = profiles.stream().map(p -> String.valueOf(p.get("userId"))).toList();
        Mono<Optional<Map<String, String>>> statuses = Caller.current()
                .flatMap(caller -> getList(caller.hr() ? SCREENING_BASE + "/sessions" : SCREENING_BASE + "/sessions/my", authorization))
                .map(sessions -> Optional.of(CandidateViews.screeningStatuses(sessions)))
                .onErrorResume(e -> {
                    if (screeningRequired) {
                        return Mono.error(BackendErrors.read("screening service", "the screening statuses", e));
                    }
                    log.warn("Screening statuses unavailable for candidates: {}", e.toString());
                    return Mono.just(Optional.empty());
                });
        Mono<Optional<Map<String, Object>>> scores = getMap(MATCHER_BASE + "/candidates/best-scores?ids=" + String.join(",", ids), null)
                .map(Optional::of)
                .onErrorResume(e -> {
                    log.warn("Match scores unavailable for candidates: {}", e.toString());
                    return Mono.just(Optional.empty());
                });
        return Mono.zip(statuses, scores).map(both -> profiles.stream().map(profile -> {
            String id = String.valueOf(profile.get("userId"));
            String screening = both.getT1().map(m -> m.getOrDefault(id, "NOT_STARTED")).orElse(null);
            Double score = both.getT2().map(m -> m.get(id)).map(MatchViews::score).orElse(null);
            return CandidateViews.candidate(profile, screening, score);
        }).toList());
    }

    private Mono<Map<String, Object>> getMap(String uri, String authorization) {
        return gatewayResilience.protect(backendFor(uri),
                        webClientBuilder.build().get().uri(URI.create(uri))
                                .headers(h -> { if (authorization != null) h.set(HttpHeaders.AUTHORIZATION, authorization); })
                                .retrieve().bodyToMono(Map.class))
                .map(this::toStringObjectMap);
    }

    private Mono<List<Map<String, Object>>> getList(String uri, String authorization) {
        return gatewayResilience.protect(backendFor(uri),
                webClientBuilder.build().get().uri(URI.create(uri))
                        .headers(h -> { if (authorization != null) h.set(HttpHeaders.AUTHORIZATION, authorization); })
                        .retrieve().bodyToFlux(Map.class)
                        .map(this::toStringObjectMap)
                        .collectList());
    }

    private static String backendFor(String uri) {
        return uri.startsWith(RESUME_PARSER_BASE) ? RESUME_PARSER_BACKEND
                : uri.startsWith(SCREENING_BASE) ? SCREENING_BACKEND : MATCHER_BACKEND;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Object raw) {
        return raw instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    private Mono<Map<String, Object>> requestMatches(String uri, String what, Integer page, Integer size) {
        return gatewayResilience.protect(MATCHER_BACKEND,
                        webClientBuilder.build().get().uri(uri)
                                .retrieve().bodyToFlux(Map.class)
                                .map(this::toStringObjectMap)
                                .collectList())
                .map(matches -> MatchViews.page(matches, page == null ? 0 : page, size == null ? 20 : size))
                .onErrorMap(e -> BackendErrors.read("matching service", what, e));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toStringObjectMap(Map<?, ?> raw) {
        return raw == null ? Map.of() : (Map<String, Object>) raw;
    }
}

