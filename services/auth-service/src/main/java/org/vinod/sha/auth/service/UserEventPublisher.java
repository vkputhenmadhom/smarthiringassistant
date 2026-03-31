package org.vinod.sha.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.vinod.sha.auth.outbox.OutboxPublisher;
import org.vinod.sha.auth.entity.User;
import org.vinod.shared.events.UserRegisteredEvent;
import org.vinod.shared.events.UserAuthenticatedEvent;

@Component
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    private final OutboxPublisher outboxPublisher;

    public UserEventPublisher(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    private static final String USER_EXCHANGE = "auth.exchange";
    private static final String USER_REGISTERED_ROUTING_KEY = "user.registered";
    private static final String USER_AUTHENTICATED_ROUTING_KEY = "user.authenticated";

    public void publishUserRegisteredEvent(User user) {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .timestamp(System.currentTimeMillis())
                .build();

        try {
            outboxPublisher.enqueue(USER_EXCHANGE, USER_REGISTERED_ROUTING_KEY, "UserRegisteredEvent", event);
            log.info("Queued UserRegisteredEvent to outbox for user: {}", user.getUsername());
        } catch (Exception e) {
            log.error("Failed to enqueue UserRegisteredEvent for user: {}", user.getUsername(), e);
        }
    }

    public void publishUserAuthenticatedEvent(User user) {
        UserAuthenticatedEvent event = UserAuthenticatedEvent.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .loginTime(System.currentTimeMillis())
                .build();

        try {
            outboxPublisher.enqueue(USER_EXCHANGE, USER_AUTHENTICATED_ROUTING_KEY, "UserAuthenticatedEvent", event);
            log.info("Queued UserAuthenticatedEvent to outbox for user: {}", user.getUsername());
        } catch (Exception e) {
            log.error("Failed to enqueue UserAuthenticatedEvent for user: {}", user.getUsername(), e);
        }
    }
}

