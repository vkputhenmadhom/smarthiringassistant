package org.vinod.sha.matcher.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class OutboxRelay {

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
                rabbitTemplate.convertAndSend(event.getExchangeName(), event.getRoutingKey(), event.getPayloadJson());
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

