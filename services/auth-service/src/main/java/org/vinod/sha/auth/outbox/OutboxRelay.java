package org.vinod.sha.auth.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxRelay(OutboxEventRepository repository, RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${events.outbox.relay-interval-ms:3000}")
    @Transactional
    public void relayPendingEvents() {
        List<OutboxEvent> pending = repository.findTop100ByStatusInOrderByCreatedAtAsc(List.of(OutboxStatus.PENDING, OutboxStatus.FAILED));
        for (OutboxEvent event : pending) {
            try {
                rabbitTemplate.send(event.getExchangeName(), event.getRoutingKey(), toJsonMessage(event.getPayloadJson()));
                event.setStatus(OutboxStatus.SENT);
                event.setSentAt(LocalDateTime.now());
                event.setUpdatedAt(LocalDateTime.now());
                repository.save(event);
            } catch (Exception ex) {
                event.setStatus(OutboxStatus.FAILED);
                event.setRetryCount(event.getRetryCount() + 1);
                event.setLastError(ex.getMessage());
                event.setUpdatedAt(LocalDateTime.now());
                repository.save(event);
                log.warn("Outbox relay failed for eventId={}", event.getEventId(), ex);
            }
        }
    }

    /**
     * The payload is already JSON. Sending it as-is with convertAndSend labels it text/plain,
     * which JSON-converting consumers (notification-service) reject and drop.
     */
    private static Message toJsonMessage(String payloadJson) {
        return MessageBuilder.withBody(payloadJson.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }

    @Transactional
    public int replayFailedEvents() {
        List<OutboxEvent> failed = repository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.FAILED);
        for (OutboxEvent e : failed) {
            e.setStatus(OutboxStatus.PENDING);
            e.setUpdatedAt(LocalDateTime.now());
            repository.save(e);
        }
        return failed.size();
    }
}

