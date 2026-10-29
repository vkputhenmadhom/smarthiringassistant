package org.vinod.sha.gateway.resilience;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GatewayResilienceTest {

    private static final String BACKEND = "job-analyzer-service";

    private final GatewayResilience resilience = resilience();

    @Test
    void readsAreRetried() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(RuntimeException.class, () -> resilience.protect(BACKEND, failing(attempts)).block());

        assertEquals(3, attempts.get());
    }

    @Test
    void writesAreNeverRetried() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(RuntimeException.class, () -> resilience.protectWrite(BACKEND, failing(attempts)).block());

        assertEquals(1, attempts.get(), "a retried write would apply the change twice");
    }

    @Test
    void aSlowWriteFinishesOnceInsteadOfTimingOutAndRunningAgain() {
        AtomicInteger readAttempts = new AtomicInteger();
        Throwable readError = assertThrows(RuntimeException.class,
                () -> resilience.protect(BACKEND, slow(readAttempts)).block());
        assertInstanceOf(TimeoutException.class, readError.getCause() == null ? readError : readError.getCause());
        assertEquals(3, readAttempts.get(), "this is how one createJob used to save three jobs");

        AtomicInteger writeAttempts = new AtomicInteger();
        assertEquals("saved", resilience.protectWrite(BACKEND, slow(writeAttempts)).block());
        assertEquals(1, writeAttempts.get());
    }

    @Test
    void writesFallBackToTheDefaultConfigWhenNoWriteConfigExists() {
        GatewayResilience plain = new GatewayResilience(CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults(),
                TimeLimiterRegistry.ofDefaults(), BulkheadRegistry.ofDefaults());

        assertEquals("ok", plain.protectWrite(BACKEND, Mono.just("ok")).block());
    }

    @Test
    void applicationYamlGivesWritesALongerTimeoutThanReads() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        Properties properties = yaml.getObject();

        assertEquals("2s", properties.getProperty("resilience4j.timelimiter.configs.default.timeoutDuration"));
        assertEquals("30s", properties.getProperty("resilience4j.timelimiter.configs.write.timeoutDuration"));
        assertEquals("30s", properties.getProperty("resilience4j.circuitbreaker.configs.write.slowCallDurationThreshold"));
    }

    private static Mono<String> failing(AtomicInteger attempts) {
        return Mono.defer(() -> {
            attempts.incrementAndGet();
            return Mono.error(new IllegalStateException("backend 503"));
        });
    }

    /** Takes longer than the read time limit but less than the write one, like createJob's AI analysis. */
    private static Mono<String> slow(AtomicInteger attempts) {
        return Mono.defer(() -> {
            attempts.incrementAndGet();
            return Mono.just("saved").delayElement(Duration.ofMillis(300));
        });
    }

    private static GatewayResilience resilience() {
        TimeLimiterRegistry timeLimiters = TimeLimiterRegistry.of(
                TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(100)).build());
        timeLimiters.addConfiguration(GatewayResilience.WRITE_CONFIG,
                TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(2)).build());
        CircuitBreakerRegistry breakers = CircuitBreakerRegistry.ofDefaults();
        breakers.addConfiguration(GatewayResilience.WRITE_CONFIG, CircuitBreakerConfig.ofDefaults());
        RetryRegistry retries = RetryRegistry.of(
                RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(10)).build());
        return new GatewayResilience(breakers, retries, timeLimiters, BulkheadRegistry.ofDefaults());
    }
}
