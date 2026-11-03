package org.vinod.sha.notification.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.vinod.sha.notification.service.NotificationService;
import org.vinod.shared.config.RabbitMQConfiguration;
import org.vinod.shared.events.UserRegisteredEvent;

@Slf4j
@Component
public class UserRegisteredListener {

    private final NotificationService notificationService;

    public UserRegisteredListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitMQConfiguration.USER_REGISTERED_QUEUE)
    public void onUserRegistered(UserRegisteredEvent event) {
        try {
            String key = "UserRegisteredEvent:" + event.getUserId();
            if (notificationService.isAlreadyProcessed(key)) {
                log.info("[UserRegisteredListener] Duplicate event skipped userId={}", event.getUserId());
                return;
            }
            log.info("[UserRegisteredListener] Processing userId={} email={}",
                    event.getUserId(), event.getEmail());
            notificationService.handleUserRegistered(event);
            notificationService.markProcessed(key, "UserRegisteredEvent");
        } catch (Exception e) {
            log.error("[UserRegisteredListener] Error processing event userId={}: {}",
                    event.getUserId(), e.getMessage(), e);
        }
    }
}
