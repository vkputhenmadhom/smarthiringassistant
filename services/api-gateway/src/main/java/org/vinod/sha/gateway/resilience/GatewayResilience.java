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

    /** Resilience4j config (application.yaml) for calls that change data: longer timeout, own breaker. */
    static final String WRITE_CONFIG = "write";
    static final String WRITE_SUFFIX = "-write";

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

    /** For reads and other calls that are safe to repeat: time limit, retry and circuit breaker. */
    public <T> Mono<T> protect(String backendName, Mono<T> source) {
        return source
                .transformDeferred(BulkheadOperator.of(bulkheadRegistry.bulkhead(backendName)))
                .transformDeferred(TimeLimiterOperator.of(timeLimiterRegistry.timeLimiter(backendName)))
                .transformDeferred(RetryOperator.of(retryRegistry.retry(backendName)))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreakerRegistry.circuitBreaker(backendName)));
    }

    /**
     * For calls that create or change data. Never retried: a timed-out attempt may still complete in the
     * backend, so a retry would apply the change twice (e.g. one createJob saving three jobs). The time
     * limit is longer because some writes run AI analysis, and slow writes use their own circuit breaker
     * so they don't open the one that guards the backend's reads.
     */
    public <T> Mono<T> protectWrite(String backendName, Mono<T> source) {
        String writeName = backendName + WRITE_SUFFIX;
        return source
                .transformDeferred(BulkheadOperator.of(bulkheadRegistry.bulkhead(backendName)))
                .transformDeferred(TimeLimiterOperator.of(timeLimiterRegistry.getConfiguration(WRITE_CONFIG)
                        .map(config -> timeLimiterRegistry.timeLimiter(writeName, config))
                        .orElseGet(() -> timeLimiterRegistry.timeLimiter(writeName))))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreakerRegistry.getConfiguration(WRITE_CONFIG)
                        .map(config -> circuitBreakerRegistry.circuitBreaker(writeName, config))
                        .orElseGet(() -> circuitBreakerRegistry.circuitBreaker(writeName))));
    }
}
