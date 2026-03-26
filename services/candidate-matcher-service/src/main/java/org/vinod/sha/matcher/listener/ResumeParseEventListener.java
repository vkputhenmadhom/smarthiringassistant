package org.vinod.sha.matcher.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.vinod.sha.matcher.entity.ProcessedEvent;
import org.vinod.sha.matcher.repository.ProcessedEventRepository;
import org.vinod.sha.matcher.service.CandidateMatcherService;
import org.vinod.shared.events.ResumeParseEvent;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeParseEventListener {

    private final CandidateMatcherService candidateMatcherService;
    private final ProcessedEventRepository processedEventRepository;

    @RabbitListener(queues = "resume.parsed.queue")
    public void handleResumeParseEvent(ResumeParseEvent event) {
        log.info("Received ResumeParseEvent for resume: {}", event.getResumeId());

        try {
            String eventKey = "ResumeParseEvent:" + event.getResumeId() + ":" + event.getStatus();
            if (processedEventRepository.existsByEventKey(eventKey)) {
                log.info("Skipping duplicate ResumeParseEvent key={}", eventKey);
                return;
            }

            if (!"SUCCESS".equals(event.getStatus())) {
                log.warn("Resume parsing failed: {}", event.getErrorMessage());
                processedEventRepository.save(ProcessedEvent.builder()
                        .eventKey(eventKey)
                        .eventType("ResumeParseEvent")
                        .processedAt(LocalDateTime.now())
                        .build());
                return;
            }

            // Match candidate with jobs based on parsed resume
            var matches = candidateMatcherService.matchCandidateWithJobs(
                    event.getUserId(),
                    event.getSkills(),
                    event.getExperienceYears(),
                    event.getLocation()
            );

            log.info("Found {} matches for candidate: {}", matches.size(), event.getUserId());
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventKey(eventKey)
                    .eventType("ResumeParseEvent")
                    .processedAt(LocalDateTime.now())
                    .build());

        } catch (Exception e) {
            log.error("Error processing ResumeParseEvent", e);
        }
    }
}

