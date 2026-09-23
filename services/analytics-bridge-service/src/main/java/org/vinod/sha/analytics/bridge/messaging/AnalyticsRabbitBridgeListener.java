package org.vinod.sha.analytics.bridge.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.vinod.sha.analytics.bridge.kafka.AnalyticsKafkaPublisher;
import org.vinod.sha.analytics.bridge.mapping.AnalyticsEventMapper;
import org.vinod.shared.analytics.events.AnalyticsEvent;
import org.vinod.shared.config.RabbitMQConfiguration;
import org.vinod.shared.events.CandidateMatchedEvent;
import org.vinod.shared.events.ScreeningCompletedEvent;

@Component
@RequiredArgsConstructor
public class AnalyticsRabbitBridgeListener {

    private final AnalyticsEventMapper mapper;
    private final AnalyticsKafkaPublisher publisher;

    @RabbitListener(queues = RabbitMQConfiguration.CANDIDATE_MATCHED_QUEUE)
    public void onCandidateMatched(CandidateMatchedEvent event) {
        AnalyticsEvent analyticsEvent = mapper.fromCandidateMatched(event);
        publisher.publishRaw(keyFor(analyticsEvent.getCandidateId(), analyticsEvent.getJobId()), analyticsEvent);
    }

    @RabbitListener(queues = RabbitMQConfiguration.SCREENING_COMPLETED_QUEUE)
    public void onScreeningCompleted(ScreeningCompletedEvent event) {
        AnalyticsEvent analyticsEvent = mapper.fromScreeningCompleted(event);
        String key = analyticsEvent.getSessionId() != null ? analyticsEvent.getSessionId() : analyticsEvent.getCandidateId();
        publisher.publishRaw(key, analyticsEvent);
        publisher.publishScreening(key, analyticsEvent);
    }

    private String keyFor(String candidateId, String jobId) {
        if (candidateId != null && !candidateId.isBlank()) {
            return candidateId;
        }
        return jobId == null ? "unknown" : jobId;
    }
}
