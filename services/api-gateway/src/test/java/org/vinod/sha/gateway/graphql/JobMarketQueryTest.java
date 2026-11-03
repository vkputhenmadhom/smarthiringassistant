package org.vinod.sha.gateway.graphql;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Candidates and visitors get the configured job market only; HR gets every job. */
class JobMarketQueryTest {

    private final List<ClientRequest> requests = new ArrayList<>();

    @Test
    void candidatesAndVisitorsAskForTheJobMarketOnly() {
        resolver().jobs(0, 20, null, null, null)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(token("ROLE_JOB_SEEKER", "ROLE_CANDIDATE"))).block();
        resolver().jobs(0, 20, null, null, null).block();

        assertTrue(requests.get(0).url().toString().endsWith("&marketOnly=true"), requests.get(0).url().toString());
        assertTrue(requests.get(1).url().toString().endsWith("&marketOnly=true"), "anonymous visitors too");
    }

    @Test
    void hrSeesEveryJob() {
        resolver().jobs(0, 20, null, null, null)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(token("ROLE_RECRUITER"))).block();

        assertFalse(requests.get(0).url().toString().contains("marketOnly"));
    }

    private JobGraphQLResolver resolver() {
        WebClient.Builder webClient = WebClient.builder().exchangeFunction(request -> {
            requests.add(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"content\":[],\"totalElements\":0,\"totalPages\":0,\"page\":0,\"size\":20}").build());
        });
        return new JobGraphQLResolver(webClient, new GatewayResilience(CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(), TimeLimiterRegistry.ofDefaults(), BulkheadRegistry.ofDefaults()));
    }

    private static JwtAuthenticationToken token(String... roles) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").claim("id", 1L).claim("sub", "u").build();
        return new JwtAuthenticationToken(jwt, java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }
}
