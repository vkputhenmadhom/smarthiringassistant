package org.vinod.shared.analytics.events;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class AnalyticsEventFactory {

    private AnalyticsEventFactory() {
    }

    public static AnalyticsEvent baseEvent(AnalyticsEventType eventType, String sourceService) {
        return AnalyticsEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType.value())
                .eventVersion(1)
                .eventTime(Instant.now())
                .sourceService(sourceService)
                .attributes(new LinkedHashMap<>())
                .build();
    }

    public static Map<String, Object> attrs(Object... keyValues) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            attributes.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return attributes;
    }
}
