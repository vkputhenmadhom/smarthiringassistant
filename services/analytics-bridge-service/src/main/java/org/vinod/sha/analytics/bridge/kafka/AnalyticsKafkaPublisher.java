package org.vinod.sha.analytics.bridge.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.vinod.shared.analytics.events.AnalyticsEvent;
import org.vinod.shared.analytics.events.AnalyticsTopics;

@Component
@RequiredArgsConstructor
public class AnalyticsKafkaPublisher {

    private final KafkaTemplate<String, AnalyticsEvent> kafkaTemplate;

    public void publishRaw(String key, AnalyticsEvent event) {
        kafkaTemplate.send(AnalyticsTopics.RAW_V1, key, event);
    }

    public void publishScreening(String key, AnalyticsEvent event) {
        kafkaTemplate.send(AnalyticsTopics.SCREENING_V1, key, event);
    }
}
