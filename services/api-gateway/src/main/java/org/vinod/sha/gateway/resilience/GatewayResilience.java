package org.vinod.sha.gateway.resilience;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GatewayResilience {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;

    public GatewayResilience(CircuitBreakerRegistry circuitBreakerRegistry,
                             RetryRegistry retryRegistry,
                             TimeLimiterRegistry timeLimiterRegistry,
                             BulkheadRegistry bulkheadRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.timeLimiterRegistry = timeLimiterRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
    }

    public <T> Mono<T> protect(String backendName, Mono<T> source) {
        return source
                .transformDeferred(BulkheadOperator.of(bulkheadRegistry.bulkhead(backendName)))
                .transformDeferred(TimeLimiterOperator.of(timeLimiterRegistry.timeLimiter(backendName)))
                .transformDeferred(RetryOperator.of(retryRegistry.retry(backendName)))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreakerRegistry.circuitBreaker(backendName)));
    }
}

