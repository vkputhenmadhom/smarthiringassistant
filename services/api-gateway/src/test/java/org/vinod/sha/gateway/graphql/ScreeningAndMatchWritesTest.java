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
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.vinod.sha.gateway.resilience.GatewayResilience;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Screening and match writes must report failures, never answer with a stub session or match. */
class ScreeningAndMatchWritesTest {

    @Test
    void startedSessionIsReturned() {
        Map<String, Object> session = screening(HttpStatus.OK, "{\"id\":\"s-1\",\"status\":\"IN_PROGRESS\"}")
                .startScreening("7", "job-1").block();

        assertEquals("s-1", session.get("id"));
    }

    @Test
    void failedStartIsAnErrorNotARandomSessionId() {
        ClientFacingException e = assertThrows(ClientFacingException.class,
                () -> screening(HttpStatus.INTERNAL_SERVER_ERROR, "{}").startScreening("7", "job-1").block());

        assertEquals(ErrorType.INTERNAL_ERROR, e.getClassification());
        assertEquals("Could not start the screening: the screening service is unavailable. Please try again shortly.", e.getMessage());
    }

    @Test
    void failedAnswerIsAnErrorNotAStubSession() {
        ClientFacingException e = assertThrows(ClientFacingException.class,
                () -> screening(HttpStatus.SERVICE_UNAVAILABLE, "{}").submitScreeningResponse("s-1", "TECHNICAL", "Java").block());

        assertEquals("Could not submit your answer for screening s-1: the screening service is unavailable. Please try again shortly.",
                e.getMessage());
    }

    @Test
    void advancingAMissingSessionIsNotFound() {
        ClientFacingException e = assertThrows(ClientFacingException.class,
                () -> screening(HttpStatus.NOT_FOUND, "{}").advanceScreening("s-1").block());

        assertEquals(ErrorType.NOT_FOUND, e.getClassification());
        assertEquals("Could not advance screening s-1: it no longer exists.", e.getMessage());
    }

    @Test
    void matchWritesAreReportedAsNotSupportedWithoutCallingTheMatcher() {
        AtomicInteger calls = new AtomicInteger();
        WebClient.Builder counting = WebClient.builder().exchangeFunction(request -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        });
        CandidateGraphQLResolver resolver = new CandidateGraphQLResolver(counting, resilience());

        ClientFacingException trigger = assertThrows(ClientFacingException.class,
                () -> resolver.triggerMatching("7", "job-1").block());
        ClientFacingException update = assertThrows(ClientFacingException.class,
                () -> resolver.updateMatchStatus("m-1", "SHORTLISTED").block());

        assertEquals(CandidateGraphQLResolver.NOT_SUPPORTED, trigger.getClassification());
        assertEquals(CandidateGraphQLResolver.NOT_SUPPORTED, update.getClassification());
        assertEquals("Changing a match's status is not supported yet, so match m-1 was not changed.", update.getMessage());
        assertEquals(0, calls.get());
    }

    private static ScreeningGraphQLResolver screening(HttpStatus status, String body) {
        WebClient.Builder webClient = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(status)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(body)
                        .build()));
        return new ScreeningGraphQLResolver(webClient, resilience());
    }

    private static GatewayResilience resilience() {
        return new GatewayResilience(CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults(),
                TimeLimiterRegistry.ofDefaults(), BulkheadRegistry.ofDefaults());
    }
}
