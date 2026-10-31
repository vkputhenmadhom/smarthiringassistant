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
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.vinod.sha.gateway.resilience.GatewayResilience;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Job reads and dashboards report a backend outage instead of showing an empty list or zeros. */
class ReadFailuresTest {

    @Test
    void jobListOutageIsAnErrorNotAnEmptyList() {
        ClientFacingException e = assertThrows(ClientFacingException.class, () -> jobs().jobs(0, 20, null, null, null)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(recruiter())).block());

        assertEquals("Could not load the jobs: the job service is unavailable. Please try again shortly.", e.getMessage());
    }

    @Test
    void singleJobOutageIsAnErrorNotABlankJob() {
        ClientFacingException e = assertThrows(ClientFacingException.class, () -> jobs().job("6ae3").block());

        assertEquals(ErrorType.INTERNAL_ERROR, e.getClassification());
    }

    @Test
    void hrDashboardOutageIsAnErrorNotZeros() {
        // Only the job service is down: with several down, whichever fails first names the outage.
        var resolver = new ScreeningGraphQLResolver(onlyJobServiceDown(), resilience());
        ClientFacingException e = assertThrows(ClientFacingException.class, () -> resolver.dashboardMetrics("Bearer x").block());

        assertEquals("Could not load the dashboard's job numbers: the job service is unavailable. Please try again shortly.", e.getMessage());
    }

    @Test
    void candidateDashboardOutageIsAnErrorNotZeros() {
        assertThrows(ClientFacingException.class, () -> screening().myCandidateDashboard("Bearer x").block());
    }

    private static JobGraphQLResolver jobs() {
        return new JobGraphQLResolver(unavailable(), resilience());
    }

    private static ScreeningGraphQLResolver screening() {
        return new ScreeningGraphQLResolver(unavailable(), resilience());
    }

    private static WebClient.Builder unavailable() {
        return WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body("{}").build()));
    }

    private static WebClient.Builder onlyJobServiceDown() {
        return WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(request.url().getHost().equals("job-analyzer-service") ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body("{}").build()));
    }

    private static GatewayResilience resilience() {
        return new GatewayResilience(CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults(),
                TimeLimiterRegistry.ofDefaults(), BulkheadRegistry.ofDefaults());
    }

    private static JwtAuthenticationToken recruiter() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").claim("id", 99L).claim("sub", "hr").build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_RECRUITER")));
    }
}
