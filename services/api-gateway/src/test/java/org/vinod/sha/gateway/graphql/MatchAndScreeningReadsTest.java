package org.vinod.sha.gateway.graphql;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.vinod.sha.gateway.resilience.GatewayResilience;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Match and screening reads go to the right backend paths, return real data or errors, and respect ownership. */
class MatchAndScreeningReadsTest {

    private static final String TWO_MATCHES = """
            [{"matchId":"m-low","candidateId":7,"jobId":"job-1","overallScore":40.0,"status":"MATCHED",
              "matchedSkills":["Java"],"missingSkills":["Kubernetes"],"createdAt":"2026-10-29T12:00:00"},
             {"matchId":"m-high","candidateId":8,"jobId":"job-1","overallScore":90.0,"status":"SHORTLISTED",
              "matchedSkills":["Java","Spring"],"missingSkills":[],"createdAt":"2026-10-29T12:05:00"}]""";

    private final List<ClientRequest> requests = new ArrayList<>();

    @Test
    void matchesForJobCallsTheMatcherAndPagesBestScoreFirst() {
        CandidateGraphQLResolver resolver = new CandidateGraphQLResolver(respondingWith(HttpStatus.OK, TWO_MATCHES), resilience());

        Map<String, Object> page = resolver.matchesForJob("job-1", 0, 1).block();

        assertEquals("http://candidate-matcher-service:8003/api/matches/job/job-1/matches", requests.get(0).url().toString());
        assertEquals(2, page.get("totalElements"));
        assertEquals(2, page.get("totalPages"));
        @SuppressWarnings("unchecked")
        Map<String, Object> best = ((List<Map<String, Object>>) page.get("content")).get(0);
        assertEquals("m-high", best.get("id"));
        assertEquals(0.9, best.get("score"));
        assertEquals("SHORTLISTED", best.get("status"));
        assertEquals(List.of("Java", "Spring"), best.get("skillMatches"));
        assertEquals("8", best.get("candidateId"));
    }

    @Test
    void unavailableMatcherIsAnErrorNotAnEmptyPage() {
        CandidateGraphQLResolver resolver = new CandidateGraphQLResolver(respondingWith(HttpStatus.SERVICE_UNAVAILABLE, "{}"), resilience());

        ClientFacingException e = assertThrows(ClientFacingException.class, () -> resolver.matchesForJob("job-1", 0, 20).block());

        assertEquals("Could not load the matches for job job-1: the matching service is unavailable. Please try again shortly.", e.getMessage());
    }

    @Test
    void aCandidateCanReadTheirOwnMatchesButNotSomeoneElses() {
        CandidateGraphQLResolver resolver = new CandidateGraphQLResolver(respondingWith(HttpStatus.OK, TWO_MATCHES), resilience());

        Map<String, Object> own = resolver.matchesForCandidate("7", 0, 20)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(candidate(7))).block();
        ClientFacingException other = assertThrows(ClientFacingException.class, () -> resolver.matchesForCandidate("8", 0, 20)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(candidate(7))).block());

        assertEquals(2, own.get("totalElements"));
        assertEquals(ErrorType.FORBIDDEN, other.getClassification());
        assertEquals(1, requests.size(), "the forbidden read never reaches the matcher");
    }

    @Test
    void updateMatchStatusPutsTheGraphqlStatusToTheMatcher() {
        CandidateGraphQLResolver resolver = new CandidateGraphQLResolver(respondingWith(HttpStatus.OK, """
                {"matchId":"m-1","candidateId":7,"jobId":"job-1","overallScore":82.0,"status":"SHORTLISTED",
                 "matchedSkills":["Java"],"missingSkills":[],"createdAt":"2026-10-29T12:00:00"}"""), resilience());

        Map<String, Object> match = resolver.updateMatchStatus("m-1", "SHORTLISTED").block();

        assertEquals("PUT", requests.get(0).method().name());
        assertEquals("http://candidate-matcher-service:8003/api/matches/m-1", requests.get(0).url().toString());
        assertEquals("SHORTLISTED", match.get("status"));
        assertEquals(0.82, match.get("score"));
    }

    @Test
    void updatingAMissingMatchIsNotFound() {
        CandidateGraphQLResolver resolver = new CandidateGraphQLResolver(respondingWith(HttpStatus.NOT_FOUND, "{}"), resilience());

        ClientFacingException e = assertThrows(ClientFacingException.class, () -> resolver.updateMatchStatus("gone", "REJECTED").block());

        assertEquals(ErrorType.NOT_FOUND, e.getClassification());
    }

    @Test
    void legacyMatcherStatusesMapOntoTheGraphqlOnes() {
        assertEquals("MATCHED", MatchViews.status("NEW"));
        assertEquals("MATCHED", MatchViews.status("REVIEWED"));
        assertEquals("SHORTLISTED", MatchViews.status("ACCEPTED"));
        assertEquals("HIRED", MatchViews.status("hired"));
        assertEquals("PENDING", MatchViews.status(null));
    }

    @Test
    void missingScreeningSessionIsNotFoundNotAStub() {
        ScreeningGraphQLResolver resolver = new ScreeningGraphQLResolver(respondingWith(HttpStatus.NOT_FOUND, "{}"), resilience());

        ClientFacingException e = assertThrows(ClientFacingException.class, () -> resolver.screeningSession("s-1")
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(recruiter())).block());

        assertEquals(ErrorType.NOT_FOUND, e.getClassification());
        assertEquals("Screening s-1 was not found.", e.getMessage());
    }

    @Test
    void aCandidateCannotOpenSomeoneElsesScreening() {
        ScreeningGraphQLResolver resolver = new ScreeningGraphQLResolver(
                respondingWith(HttpStatus.OK, "{\"id\":\"s-1\",\"candidateId\":8,\"status\":\"IN_PROGRESS\"}"), resilience());

        Map<String, Object> asHr = resolver.screeningSession("s-1")
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(recruiter())).block();
        ClientFacingException asOther = assertThrows(ClientFacingException.class, () -> resolver.screeningSession("s-1")
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(candidate(7))).block());

        assertEquals("s-1", asHr.get("id"));
        assertEquals(ErrorType.FORBIDDEN, asOther.getClassification());
    }

    @Test
    void candidatesListOnlyTheirOwnSessionsAndHrListsAll() {
        ScreeningGraphQLResolver resolver = new ScreeningGraphQLResolver(respondingWith(HttpStatus.OK, "[]"), resilience());

        resolver.myScreeningSessions("Bearer cand").contextWrite(ReactiveSecurityContextHolder.withAuthentication(candidate(7))).block();
        resolver.myScreeningSessions("Bearer hr").contextWrite(ReactiveSecurityContextHolder.withAuthentication(recruiter())).block();

        assertEquals("http://screening-bot-service:8006/api/screening/sessions/my", requests.get(0).url().toString());
        assertEquals("Bearer cand", requests.get(0).headers().getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("http://screening-bot-service:8006/api/screening/sessions", requests.get(1).url().toString());
    }

    @Test
    void unavailableScreeningServiceIsAnErrorNotAFakeSession() {
        ScreeningGraphQLResolver resolver = new ScreeningGraphQLResolver(respondingWith(HttpStatus.SERVICE_UNAVAILABLE, "{}"), resilience());

        ClientFacingException e = assertThrows(ClientFacingException.class, () -> resolver.myScreeningSessions("Bearer hr")
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(recruiter())).block());

        assertEquals(ErrorType.INTERNAL_ERROR, e.getClassification());
    }

    private WebClient.Builder respondingWith(HttpStatus status, String body) {
        return WebClient.builder().exchangeFunction(request -> {
            requests.add(request);
            return Mono.just(ClientResponse.create(status)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build());
        });
    }

    private static JwtAuthenticationToken candidate(long id) {
        return token(id, "ROLE_JOB_SEEKER", "ROLE_CANDIDATE");
    }

    private static JwtAuthenticationToken recruiter() {
        return token(99, "ROLE_RECRUITER");
    }

    private static JwtAuthenticationToken token(long id, String... roles) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").claim("id", id).claim("sub", "u" + id).build();
        return new JwtAuthenticationToken(jwt, java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }

    private static GatewayResilience resilience() {
        return new GatewayResilience(CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults(),
                TimeLimiterRegistry.ofDefaults(), BulkheadRegistry.ofDefaults());
    }
}
