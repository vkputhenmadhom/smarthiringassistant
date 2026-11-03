package org.vinod.sha.gateway.graphql;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
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

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Job writes must report failures, never answer with a placeholder job the dashboard would show as saved. */
class JobGraphQLResolverTest {

    private static final Map<String, Object> INPUT = Map.of("title", "Platform Engineer", "description", "Java");

    @Test
    void createdJobIsReturned() {
        JobGraphQLResolver resolver = resolverRespondingWith(HttpStatus.OK, """
                {"id":"6ae3","jobTitle":"Platform Engineer","status":"DRAFT"}""");

        Map<String, Object> job = resolver.createJob(INPUT).block();

        assertEquals("6ae3", job.get("id"));
        assertEquals("DRAFT", job.get("status"));
    }

    @Test
    void failedCreateIsAnErrorNotAPlaceholderJob() {
        JobGraphQLResolver resolver = resolverRespondingWith(HttpStatus.INTERNAL_SERVER_ERROR, "{}");

        ClientFacingException e = assertThrows(ClientFacingException.class, () -> resolver.createJob(INPUT).block());

        assertEquals(ErrorType.INTERNAL_ERROR, e.getClassification());
        assertEquals("Could not create the job: the job service is unavailable. Please try again shortly.", e.getMessage());
    }

    @Test
    void timedOutCreateWarnsThatTheJobMayStillBeSaved() {
        WebClient.Builder neverAnswers = WebClient.builder().exchangeFunction(request -> Mono.never());
        TimeLimiterRegistry fastTimeout = TimeLimiterRegistry.of(
                TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(100)).build());
        JobGraphQLResolver resolver = new JobGraphQLResolver(neverAnswers, new GatewayResilience(
                CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults(), fastTimeout, BulkheadRegistry.ofDefaults()));

        ClientFacingException e = assertThrows(ClientFacingException.class, () -> resolver.createJob(INPUT).block());

        assertTrue(e.getMessage().contains("may still go through"), e.getMessage());
    }

    @Test
    void publishingAMissingJobIsNotFound() {
        JobGraphQLResolver resolver = resolverRespondingWith(HttpStatus.NOT_FOUND, "{}");

        ClientFacingException e = assertThrows(ClientFacingException.class, () -> resolver.publishJob("6ae3").block());

        assertEquals(ErrorType.NOT_FOUND, e.getClassification());
        assertEquals("Could not publish job 6ae3: it no longer exists.", e.getMessage());
    }

    @Test
    void rejectedUpdateShowsThePlainTextReason() {
        JobGraphQLResolver resolver = resolverRespondingWith(HttpStatus.BAD_REQUEST, "Salary minimum exceeds maximum");

        ClientFacingException e = assertThrows(ClientFacingException.class, () -> resolver.updateJob("6ae3", INPUT).block());

        assertEquals(ErrorType.BAD_REQUEST, e.getClassification());
        assertEquals("Could not update job 6ae3: Salary minimum exceeds maximum", e.getMessage());
    }

    @Test
    void rejectedUpdateHidesSpringsJsonErrorDocument() {
        JobGraphQLResolver resolver = resolverRespondingWith(HttpStatus.BAD_REQUEST,
                "{\"timestamp\":\"2026-10-29T15:00:00\",\"status\":400,\"error\":\"Bad Request\",\"path\":\"/api/jobs/jobs/6ae3\"}");

        ClientFacingException e = assertThrows(ClientFacingException.class, () -> resolver.updateJob("6ae3", INPUT).block());

        assertEquals("Could not update job 6ae3: the job service rejected it (400)", e.getMessage());
    }

    @Test
    void failedDeleteIsAnErrorNotFalse() {
        JobGraphQLResolver resolver = resolverRespondingWith(HttpStatus.SERVICE_UNAVAILABLE, "{}");

        ClientFacingException e = assertThrows(ClientFacingException.class, () -> resolver.deleteJob("6ae3").block());

        assertEquals(ErrorType.INTERNAL_ERROR, e.getClassification());
    }

    @Test
    void successfulDeleteIsTrue() {
        assertEquals(Boolean.TRUE, resolverRespondingWith(HttpStatus.NO_CONTENT, "").deleteJob("6ae3").block());
    }

    private static JobGraphQLResolver resolverRespondingWith(HttpStatus status, String body) {
        WebClient.Builder webClient = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(status)
                        .header(HttpHeaders.CONTENT_TYPE, body.startsWith("{")
                                ? MediaType.APPLICATION_JSON_VALUE : MediaType.TEXT_PLAIN_VALUE)
                        .body(body)
                        .build()));
        GatewayResilience resilience = new GatewayResilience(CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(), TimeLimiterRegistry.ofDefaults(), BulkheadRegistry.ofDefaults());
        return new JobGraphQLResolver(webClient, resilience);
    }
}
