package org.vinod.sha.matcher.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }


    @Transactional
    public void enqueue(String exchange, String routingKey, String eventType, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            LocalDateTime now = LocalDateTime.now();
            OutboxEvent event = OutboxEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .exchangeName(exchange)
                    .routingKey(routingKey)
                    .eventType(eventType)
                    .payloadJson(json)
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            repository.save(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to enqueue outbox event", e);
        }
    }
}

