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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Exercises the resolver against canned auth-service HTTP responses. */
class AuthGraphQLResolverTest {

    @Test
    void pendingHrRegistrationBecomesPendingApprovalError() {
        AuthGraphQLResolver resolver = resolverRespondingWith(HttpStatus.ACCEPTED, """
                {"accessToken":null,"refreshToken":null,"approvalStatus":"PENDING",
                 "user":{"id":7,"username":"pat","role":"ADMIN","approvalStatus":"PENDING"}}""");

        AuthFlowException e = assertThrows(AuthFlowException.class,
                () -> resolver.register("pat", "pat@example.com", "password123", "HR_ADMIN").block());

        assertEquals(AuthFlowException.PENDING_APPROVAL, e.getClassification());
    }

    @Test
    void approvedRegistrationReturnsTokens() {
        AuthGraphQLResolver resolver = resolverRespondingWith(HttpStatus.CREATED, """
                {"accessToken":"access","refreshToken":"refresh","expiresIn":3600,"approvalStatus":"APPROVED",
                 "user":{"id":8,"username":"sam","role":"JOB_SEEKER","approvalStatus":"APPROVED"}}""");

        Map<String, Object> payload = resolver.register("sam", "sam@example.com", "password123", "CANDIDATE").block();

        assertEquals("access", payload.get("token"));
    }

    @Test
    void wrongPasswordSurfacesAuthServiceMessageInsteadOfAFakeSession() {
        AuthGraphQLResolver resolver = resolverRespondingWith(HttpStatus.BAD_REQUEST, "Invalid username or password");

        AuthFlowException e = assertThrows(AuthFlowException.class, () -> resolver.login("sam", "nope").block());

        assertEquals(ErrorType.BAD_REQUEST, e.getClassification());
        assertEquals("Invalid username or password", e.getMessage());
    }

    @Test
    void pendingLoginIsForbiddenWithReason() {
        AuthGraphQLResolver resolver = resolverRespondingWith(HttpStatus.FORBIDDEN, "Your account is awaiting administrator approval");

        AuthFlowException e = assertThrows(AuthFlowException.class, () -> resolver.login("pat", "password123").block());

        assertEquals(ErrorType.FORBIDDEN, e.getClassification());
        assertEquals("Your account is awaiting administrator approval", e.getMessage());
    }

    private static AuthGraphQLResolver resolverRespondingWith(HttpStatus status, String body) {
        WebClient.Builder webClient = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(status)
                        .header(HttpHeaders.CONTENT_TYPE, body.startsWith("{")
                                ? MediaType.APPLICATION_JSON_VALUE : MediaType.TEXT_PLAIN_VALUE)
                        .body(body)
                        .build()));
        GatewayResilience resilience = new GatewayResilience(CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(), TimeLimiterRegistry.ofDefaults(), BulkheadRegistry.ofDefaults());
        return new AuthGraphQLResolver(webClient, resilience);
    }
}
