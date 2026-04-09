package org.vinod.sha.gateway.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SubscriptionGraphQLResolverTest {

    @Test
    void allowsNotificationSubscriptionForOwnUserId() {
        GatewaySubscriptionBroker broker = new GatewaySubscriptionBroker();
        SubscriptionGraphQLResolver resolver = new SubscriptionGraphQLResolver(broker);

        StepVerifier.create(resolver.newNotification("42", bearerTokenForUserId("42")).take(1))
                .then(() -> broker.publishNotification("42", Map.of("userId", "42", "type", "MATCH")))
                .assertNext(payload -> org.junit.jupiter.api.Assertions.assertEquals("42", payload.get("userId")))
                .verifyComplete();
    }

    @Test
    void rejectsNotificationSubscriptionWithoutAuthorization() {
        GatewaySubscriptionBroker broker = new GatewaySubscriptionBroker();
        SubscriptionGraphQLResolver resolver = new SubscriptionGraphQLResolver(broker);

        assertThrows(AccessDeniedException.class, () -> resolver.newNotification("42", null));
    }

    @Test
    void rejectsNotificationSubscriptionForDifferentUserId() {
        GatewaySubscriptionBroker broker = new GatewaySubscriptionBroker();
        SubscriptionGraphQLResolver resolver = new SubscriptionGraphQLResolver(broker);

        assertThrows(AccessDeniedException.class,
                () -> resolver.newNotification("99", bearerTokenForUserId("42")));
    }

    @Test
    void rejectsNotificationSubscriptionForMalformedToken() {
        GatewaySubscriptionBroker broker = new GatewaySubscriptionBroker();
        SubscriptionGraphQLResolver resolver = new SubscriptionGraphQLResolver(broker);

        assertThrows(AccessDeniedException.class,
                () -> resolver.newNotification("42", "Bearer not-a-jwt"));
    }

    private String bearerTokenForUserId(String userId) {
        String headerJson = "{\"alg\":\"none\",\"typ\":\"JWT\"}";
        String payloadJson = "{\"id\":\"" + userId + "\"}";
        return "Bearer "
                + Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8))
                + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8))
                + ".signature";
    }
}

