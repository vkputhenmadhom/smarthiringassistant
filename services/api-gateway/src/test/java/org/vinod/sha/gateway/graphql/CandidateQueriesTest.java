package org.vinod.sha.gateway.graphql;

import graphql.GraphQLContext;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Candidate queries read real profiles from resume-parser and add screening status and match score. */
class CandidateQueriesTest {

    private static final String PAGE = """
            {"content":[
              {"userId":7,"name":"Jane Doe","email":"jane@example.com","skills":["Java"],"parseStatus":"COMPLETED",
               "experience":[{"company":"Acme","title":"Engineer"}],"education":[],"createdAt":"2026-10-30T09:00:00"},
              {"userId":8,"name":null,"email":null,"skills":[],"parseStatus":"PROCESSING","experience":[],"education":[]}],
             "totalElements":2,"totalPages":1,"page":0,"size":20}""";
    private static final String SESSIONS = """
            [{"candidateId":7,"status":"IN_PROGRESS","createdAt":"2026-10-29T10:00:00"},
             {"candidateId":7,"status":"COMPLETED","decision":"PASS","createdAt":"2026-10-30T10:00:00"}]""";
    private static final String SCORES = "{\"7\":82.0}";

    private final List<ClientRequest> requests = new ArrayList<>();
    private final Map<String, HttpStatus> down = new LinkedHashMap<>();

    @Test
    void hrListsRealCandidatesWithScreeningStatusAndBestScore() {
        Map<String, Object> page = resolver().candidates(0, 20, null, null, "Bearer hr")
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(recruiter())).block();

        List<Map<String, Object>> content = content(page);
        assertEquals("Jane Doe", content.get(0).get("name"));
        assertEquals("PASSED", content.get(0).get("screeningStatus"), "latest session decides");
        assertEquals(0.82, content.get(0).get("matchScore"));
        assertEquals("Candidate #8", content.get(1).get("name"), "unparsed resume still has a readable name");
        assertEquals("NOT_STARTED", content.get(1).get("screeningStatus"));
        assertNull(content.get(1).get("matchScore"));
        assertEquals(List.of(), ((Map<?, ?>) ((List<?>) content.get(0).get("experience")).get(0)).get("technologies"));
        ClientRequest toResumeParser = requests.get(0);
        assertEquals("http://resume-parser-service:8002/api/resumes/candidates?page=0&size=20", toResumeParser.url().toString());
        assertEquals("Bearer hr", toResumeParser.headers().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void filteringByScreeningStatusPagesInTheGateway() {
        Map<String, Object> page = resolver().candidates(0, 20, "NOT_STARTED", null, "Bearer hr")
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(recruiter())).block();

        assertEquals(1, page.get("totalElements"));
        assertEquals("8", content(page).get(0).get("id"));
        assertTrue(requests.get(0).url().toString().contains("size=1000"));
    }

    @Test
    void screeningOutageLeavesStatusUnknownInsteadOfFailingTheList() {
        down.put("screening-bot-service", HttpStatus.SERVICE_UNAVAILABLE);

        Map<String, Object> page = resolver().candidates(0, 20, null, null, "Bearer hr")
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(recruiter())).block();

        assertNull(content(page).get(0).get("screeningStatus"));
        assertEquals(0.82, content(page).get(0).get("matchScore"));
    }

    @Test
    void resumeServiceOutageIsAnErrorNotStubCandidates() {
        down.put("resume-parser-service", HttpStatus.SERVICE_UNAVAILABLE);

        ClientFacingException e = assertThrows(ClientFacingException.class, () -> resolver().candidates(0, 20, null, null, "Bearer hr")
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(recruiter())).block());

        assertEquals("Could not load the candidates: the resume service is unavailable. Please try again shortly.", e.getMessage());
    }

    @Test
    void aCandidateCannotOpenSomeoneElsesProfile() {
        ClientFacingException e = assertThrows(ClientFacingException.class, () -> resolver().candidate("8", "Bearer c7")
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(candidate(7))).block());

        assertEquals(ErrorType.FORBIDDEN, e.getClassification());
        assertTrue(requests.isEmpty());
    }

    @Test
    void myProfileWithoutAResumeAsksForOne() {
        down.put("resume-parser-service", HttpStatus.NOT_FOUND);

        ClientFacingException e = assertThrows(ClientFacingException.class, () -> resolver().myProfile("Bearer c7")
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(candidate(7))).block());

        assertEquals(ErrorType.NOT_FOUND, e.getClassification());
        assertEquals("Upload a resume to create your profile.", e.getMessage());
        assertEquals("http://resume-parser-service:8002/api/resumes/candidates/7", requests.get(0).url().toString());
    }

    @Test
    void matchRowsGetTheirCandidatesInOneCall() {
        Map<String, Object> forJane = new LinkedHashMap<>(Map.of("id", "m-1", "candidateId", "7"));
        Map<String, Object> forGhost = new LinkedHashMap<>(Map.of("id", "m-2", "candidateId", "99"));
        GraphQLContext context = GraphQLContext.newContext().of("Authorization", "Bearer hr").build();

        Map<Map<String, Object>, Map<String, Object>> result = resolver().candidate(List.of(forJane, forGhost), context).block();

        assertEquals("Jane Doe", result.get(forJane).get("name"));
        assertNull(result.get(forGhost), "a candidate without a resume leaves the field null");
        assertEquals("http://resume-parser-service:8002/api/resumes/candidates?userIds=7,99", requests.get(0).url().toString());
    }

    private CandidateGraphQLResolver resolver() {
        WebClient.Builder webClient = WebClient.builder().exchangeFunction(request -> {
            requests.add(request);
            String host = request.url().getHost();
            if (down.containsKey(host)) {
                return Mono.just(response(down.get(host), "{}"));
            }
            String path = request.url().getPath();
            String body = path.endsWith("/best-scores") ? SCORES
                    : host.startsWith("screening") ? SESSIONS
                    : request.url().getQuery() != null && request.url().getQuery().startsWith("userIds") ? "[" + jane() + "]"
                    : path.matches(".*/candidates/\\d+") ? jane()
                    : PAGE;
            return Mono.just(response(HttpStatus.OK, body));
        });
        return new CandidateGraphQLResolver(webClient, new GatewayResilience(CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(), TimeLimiterRegistry.ofDefaults(), BulkheadRegistry.ofDefaults()));
    }

    private static String jane() {
        return "{\"userId\":7,\"name\":\"Jane Doe\",\"skills\":[],\"experience\":[],\"education\":[],\"parseStatus\":\"COMPLETED\"}";
    }

    private static ClientResponse response(HttpStatus status, String body) {
        return ClientResponse.create(status).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(body).build();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> content(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("content");
    }

    private static JwtAuthenticationToken candidate(long id) {
        return token(id, "ROLE_JOB_SEEKER", "ROLE_CANDIDATE");
    }

    private static JwtAuthenticationToken recruiter() {
        return token(99, "ROLE_RECRUITER");
    }

    private static JwtAuthenticationToken token(long id, String... roles) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").claim("id", id).claim("sub", "u" + id).build();
        return new JwtAuthenticationToken(jwt, Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }
}
