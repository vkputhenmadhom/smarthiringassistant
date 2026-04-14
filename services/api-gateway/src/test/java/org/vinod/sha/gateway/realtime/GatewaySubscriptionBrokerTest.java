package org.vinod.sha.gateway.realtime;

import org.junit.jupiter.api.Test;
import org.vinod.shared.events.CandidateMatchedEvent;
import org.vinod.shared.events.ScreeningCompletedEvent;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

class GatewaySubscriptionBrokerTest {

    @Test
    void relaysBackplaneMessageFromDifferentInstanceToLocalSubscribers() {
        GatewaySubscriptionBroker broker = new GatewaySubscriptionBroker();

        StepVerifier.create(broker.notificationUpdates("42").take(1))
                .then(() -> broker.onBackplaneMessage(
                        "instance-b",
                        RealtimeChannel.NOTIFICATION,
                        "42",
                        Map.of("userId", "42", "type", "MATCH")
                ))
                .assertNext(payload -> {
                    org.junit.jupiter.api.Assertions.assertEquals("42", payload.get("userId"));
                    org.junit.jupiter.api.Assertions.assertEquals("MATCH", payload.get("type"));
                })
                .verifyComplete();
    }

    @Test
    void ignoresBackplaneEchoFromSameInstance() {
        GatewaySubscriptionBroker broker = new GatewaySubscriptionBroker();

        StepVerifier.create(broker.notificationUpdates("42").take(1))
                .then(() -> broker.onBackplaneMessage(
                        broker.getInstanceId(),
                        RealtimeChannel.NOTIFICATION,
                        "42",
                        Map.of("userId", "42", "type", "MATCH")
                ))
                .expectTimeout(java.time.Duration.ofMillis(200))
                .verify();
    }

    @Test
    void publishesMatchUpdateToJobSubscribers() {
        GatewaySubscriptionBroker broker = new GatewaySubscriptionBroker();

        StepVerifier.create(broker.matchUpdates("job-101").take(1))
                .then(() -> broker.publishMatchUpdate("job-101", Map.of("jobId", "job-101", "status", "MATCHED")))
                .assertNext(payload -> {
                    org.junit.jupiter.api.Assertions.assertEquals("job-101", payload.get("jobId"));
                    org.junit.jupiter.api.Assertions.assertEquals("MATCHED", payload.get("status"));
                })
                .verifyComplete();
    }

    @Test
    void listenerPublishesMatchAndNotificationFromCandidateMatchedEvent() {
        GatewaySubscriptionBroker broker = new GatewaySubscriptionBroker();
        GatewayRabbitSubscriptionListener listener = new GatewayRabbitSubscriptionListener(broker);

        CandidateMatchedEvent event = CandidateMatchedEvent.builder()
                .matchId("m-1")
                .candidateId(42L)
                .jobId("job-900")
                .matchScore(87.0)
                .status("MATCHED")
                .matchedAt(1712668800L)
                .build();

        StepVerifier.create(broker.matchUpdates("job-900").take(1))
                .then(() -> listener.onCandidateMatched(event))
                .assertNext(payload -> {
                    org.junit.jupiter.api.Assertions.assertEquals("m-1", payload.get("id"));
                    org.junit.jupiter.api.Assertions.assertEquals("job-900", payload.get("jobId"));
                    org.junit.jupiter.api.Assertions.assertEquals("MATCHED", payload.get("status"));
                })
                .verifyComplete();

        StepVerifier.create(broker.notificationUpdates("42").take(1))
                .then(() -> listener.onCandidateMatched(event))
                .assertNext(payload -> {
                    org.junit.jupiter.api.Assertions.assertEquals("42", payload.get("userId"));
                    org.junit.jupiter.api.Assertions.assertEquals("MATCH", payload.get("type"));
                })
                .verifyComplete();
    }

    @Test
    void listenerPublishesScreeningAndNotificationFromScreeningCompletedEvent() {
        GatewaySubscriptionBroker broker = new GatewaySubscriptionBroker();
        GatewayRabbitSubscriptionListener listener = new GatewayRabbitSubscriptionListener(broker);

        ScreeningCompletedEvent event = ScreeningCompletedEvent.builder()
                .sessionId("sess-77")
                .candidateId(9L)
                .jobId("job-22")
                .decision("PASSED")
                .finalScore(0.92)
                .failureReasons(List.of())
                .completedAt(1712668800L)
                .build();

        StepVerifier.create(broker.screeningUpdates("sess-77").take(1))
                .then(() -> listener.onScreeningCompleted(event))
                .assertNext(payload -> {
                    org.junit.jupiter.api.Assertions.assertEquals("sess-77", payload.get("id"));
                    org.junit.jupiter.api.Assertions.assertEquals("PASSED", payload.get("decision"));
                    org.junit.jupiter.api.Assertions.assertEquals("COMPLETED", payload.get("status"));
                })
                .verifyComplete();

        StepVerifier.create(broker.notificationUpdates("9").take(1))
                .then(() -> listener.onScreeningCompleted(event))
                .assertNext(payload -> {
                    org.junit.jupiter.api.Assertions.assertEquals("9", payload.get("userId"));
                    org.junit.jupiter.api.Assertions.assertEquals("SCREENING", payload.get("type"));
                })
                .verifyComplete();
    }
}

