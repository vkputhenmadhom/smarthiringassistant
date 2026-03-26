package org.vinod.sha.resumeparser.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
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

