package org.vinod.sha.notification.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.vinod.sha.notification.service.NotificationService;
import org.vinod.shared.config.RabbitMQConfiguration;
import org.vinod.shared.events.CandidateMatchedEvent;

@Slf4j
@Component
public class CandidateMatchedListener {

    private final NotificationService notificationService;

    public CandidateMatchedListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitMQConfiguration.CANDIDATE_MATCHED_QUEUE)
    public void onCandidateMatched(CandidateMatchedEvent event) {
        try {
            String key = "CandidateMatchedEvent:" + event.getMatchId();
            if (notificationService.isAlreadyProcessed(key)) {
                log.info("[CandidateMatchedListener] Duplicate event skipped matchId={}", event.getMatchId());
                return;
            }
            log.info("[CandidateMatchedListener] Processing matchId={} candidateId={}",
                    event.getMatchId(), event.getCandidateId());
            notificationService.handleCandidateMatched(event);
            notificationService.markProcessed(key, "CandidateMatchedEvent");
        } catch (Exception e) {
            log.error("[CandidateMatchedListener] Error processing event matchId={}: {}",
                    event.getMatchId(), e.getMessage(), e);
        }
    }
}
