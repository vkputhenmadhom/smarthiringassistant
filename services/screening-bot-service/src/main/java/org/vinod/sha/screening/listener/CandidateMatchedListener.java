package org.vinod.sha.screening.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.vinod.shared.events.CandidateMatchedEvent;
import org.vinod.sha.screening.dto.CreateSessionRequest;
import org.vinod.sha.screening.entity.ConsumedEvent;
import org.vinod.sha.screening.repository.ConsumedEventRepository;
import org.vinod.sha.screening.service.ScreeningBotService;

import java.time.LocalDateTime;

@Slf4j
@Component
public class CandidateMatchedListener {

    private final ScreeningBotService screeningBotService;
    private final ConsumedEventRepository consumedEventRepository;

    public CandidateMatchedListener(ScreeningBotService screeningBotService,
                                    ConsumedEventRepository consumedEventRepository) {
        this.screeningBotService = screeningBotService;
        this.consumedEventRepository = consumedEventRepository;
    }

    @RabbitListener(queues = "candidate.matched.queue")
    public void onCandidateMatched(CandidateMatchedEvent event) {
        try {
            String eventKey = "CandidateMatchedEvent:" + event.getMatchId();
            if (consumedEventRepository.existsByEventKey(eventKey)) {
                log.info("Skipping duplicate CandidateMatchedEvent key={}", eventKey);
                return;
            }

            // Auto-create a screening session for high-confidence matches.
            if (event.getMatchScore() != null && event.getMatchScore() >= 50.0) {
                screeningBotService.createSession(CreateSessionRequest.builder()
                        .candidateId(event.getCandidateId())
                        .jobId(event.getJobId())
                        .build());
                log.info("Created screening session from matched event for candidate={} job={}",
                        event.getCandidateId(), event.getJobId());
            }
            consumedEventRepository.save(ConsumedEvent.builder()
                    .eventKey(eventKey)
                    .eventType("CandidateMatchedEvent")
                    .consumedAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.error("Failed to process CandidateMatchedEvent", e);
        }
    }
}

