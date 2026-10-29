package org.vinod.sha.notification.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.vinod.sha.notification.service.NotificationService;
import org.vinod.shared.config.RabbitMQConfiguration;
import org.vinod.shared.events.ScreeningCompletedEvent;

@Slf4j
@Component
public class ScreeningCompletedListener {

    private final NotificationService notificationService;

    public ScreeningCompletedListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitMQConfiguration.SCREENING_COMPLETED_NOTIFICATION_QUEUE)
    public void onScreeningCompleted(ScreeningCompletedEvent event) {
        try {
            String key = "ScreeningCompletedEvent:" + event.getSessionId();
            if (notificationService.isAlreadyProcessed(key)) {
                log.info("[ScreeningCompletedListener] Duplicate event skipped sessionId={}", event.getSessionId());
                return;
            }
            log.info("[ScreeningCompletedListener] Processing sessionId={} decision={}",
                    event.getSessionId(), event.getDecision());
            notificationService.handleScreeningCompleted(event);
            notificationService.markProcessed(key, "ScreeningCompletedEvent");
        } catch (Exception e) {
            log.error("[ScreeningCompletedListener] Error processing event sessionId={}: {}",
                    event.getSessionId(), e.getMessage(), e);
        }
    }
}
