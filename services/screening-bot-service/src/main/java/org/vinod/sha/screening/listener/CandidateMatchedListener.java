package org.vinod.sha.screening.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vinod.shared.config.RabbitMQConfiguration;
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
    private final boolean autoStartOnMatch;
    private final double autoStartMinScore;

    /**
     * @param autoStartOnMatch start a screening session for every good match (screening.auto-start-on-match).
     *                         Off by default: a session starts when the candidate applies, otherwise every
     *                         resume upload opens one session per matched job.
     */
    public CandidateMatchedListener(ScreeningBotService screeningBotService,
                                    ConsumedEventRepository consumedEventRepository,
                                    @Value("${screening.auto-start-on-match:false}") boolean autoStartOnMatch,
                                    @Value("${screening.auto-start-min-score:50}") double autoStartMinScore) {
        this.screeningBotService = screeningBotService;
        this.consumedEventRepository = consumedEventRepository;
        this.autoStartOnMatch = autoStartOnMatch;
        this.autoStartMinScore = autoStartMinScore;
    }

    @RabbitListener(queues = RabbitMQConfiguration.CANDIDATE_MATCHED_SCREENING_QUEUE)
    public void onCandidateMatched(CandidateMatchedEvent event) {
        try {
            String eventKey = "CandidateMatchedEvent:" + event.getMatchId();
            if (consumedEventRepository.existsByEventKey(eventKey)) {
                log.info("Skipping duplicate CandidateMatchedEvent key={}", eventKey);
                return;
            }

            if (autoStartOnMatch && event.getMatchScore() != null && event.getMatchScore() >= autoStartMinScore) {
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

