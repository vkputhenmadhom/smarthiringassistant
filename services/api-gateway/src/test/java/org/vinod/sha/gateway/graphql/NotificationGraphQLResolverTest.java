package org.vinod.sha.gateway.graphql;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationGraphQLResolverTest {

    @Test
    void mapsAStoredNotificationOntoTheGraphqlType() {
        Map<String, Object> n = NotificationGraphQLResolver.notification(Map.of(
                "id", 5, "userId", 32, "type", "SHORTLISTED", "title", "You've been shortlisted for Principal Engineer",
                "message", "Next steps soon.", "read", false, "createdAt", "2026-11-02T08:15:30"));

        assertEquals("5", n.get("id"));
        assertEquals("32", n.get("userId"));
        assertEquals("SHORTLISTED", n.get("type"));
        assertEquals(false, n.get("read"));
        assertEquals("2026-11-02T08:15:30Z", n.get("createdAt"));
    }
}
