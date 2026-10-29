package org.vinod.sha.notification.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.vinod.shared.config.RabbitMQConfiguration;
import org.vinod.shared.events.UserRegisteredEvent;
import org.vinod.sha.notification.service.NotificationService;

@Slf4j
@Component
public class UserRegisteredListener {

    private final NotificationService notificationService;

    public UserRegisteredListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitMQConfiguration.USER_REGISTERED_QUEUE)
    public void onUserRegistered(UserRegisteredEvent event) {
        if (event == null) {
            return;
        }

        String eventKey = "UserRegisteredEvent:" + event.getUserId();
        if (notificationService.isAlreadyProcessed(eventKey)) {
            log.info("Skipping duplicate UserRegisteredEvent key={}", eventKey);
            return;
        }

        notificationService.handleUserRegistered(event);
        notificationService.markProcessed(eventKey, "UserRegisteredEvent");
    }
}
