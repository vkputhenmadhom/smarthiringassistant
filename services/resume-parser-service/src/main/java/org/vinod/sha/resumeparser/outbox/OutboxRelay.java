package org.vinod.sha.resumeparser.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class OutboxRelay {

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate claimTx;
    private final TransactionTemplate eventTx;
    private final int batchSize;
    private final int maxRetries;
    private final boolean deadLetterEnabled;

    public OutboxRelay(OutboxEventRepository repository,
                       RabbitTemplate rabbitTemplate,
                       PlatformTransactionManager transactionManager,
                       @Value("${events.outbox.batch-size:100}") int batchSize,
                       @Value("${events.outbox.max-retries:5}") int maxRetries,
                       @Value("${events.outbox.dead-letter-enabled:true}") boolean deadLetterEnabled) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
        this.deadLetterEnabled = deadLetterEnabled;

        this.claimTx = new TransactionTemplate(transactionManager);
        this.eventTx = new TransactionTemplate(transactionManager);
        this.eventTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${events.outbox.relay-interval-ms:3000}")
    public void relayPendingEvents() {
        List<Long> claimedIds = claimPendingBatch();
        for (Long id : claimedIds) {
            eventTx.executeWithoutResult(status -> processClaimedEvent(id));
        }
    }

    public List<Long> claimPendingBatch() {
        return claimTx.execute(status -> {
            List<OutboxStatus> claimable = List.of(OutboxStatus.PENDING, OutboxStatus.FAILED);
            List<Long> ids = repository.lockIdsByStatusInOrderByCreatedAtAsc(claimable, PageRequest.of(0, batchSize));
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }
            repository.transitionStatusByIds(ids, claimable, OutboxStatus.PROCESSING, LocalDateTime.now());
            return ids;
        });
    }

    private void processClaimedEvent(Long id) {
        repository.findByIdAndStatus(id, OutboxStatus.PROCESSING).ifPresent(event -> {
            try {
                rabbitTemplate.convertAndSend(event.getExchangeName(), event.getRoutingKey(), event.getPayloadJson());
                event.setStatus(OutboxStatus.SENT);
                event.setSentAt(LocalDateTime.now());
                event.setLastError(null);
                event.setUpdatedAt(LocalDateTime.now());
                repository.save(event);
            } catch (Exception ex) {
                int nextRetryCount = event.getRetryCount() + 1;
                boolean exhausted = nextRetryCount >= maxRetries;
                event.setRetryCount(nextRetryCount);
                event.setStatus(exhausted && deadLetterEnabled ? OutboxStatus.DEAD_LETTER : OutboxStatus.FAILED);
                event.setLastError(ex.getMessage());
                event.setUpdatedAt(LocalDateTime.now());
                repository.save(event);
                log.warn("Outbox relay failed for eventId={}, retryCount={}", event.getEventId(), nextRetryCount, ex);
            }
        });
    }

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

