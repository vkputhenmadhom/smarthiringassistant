package org.vinod.sha.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.vinod.sha.notification.entity.NotificationLog;
import org.vinod.sha.notification.entity.ProcessedEvent;
import org.vinod.sha.notification.repository.NotificationLogRepository;
import org.vinod.sha.notification.repository.ProcessedEventRepository;
import org.vinod.shared.events.CandidateMatchedEvent;
import org.vinod.shared.events.InterviewScheduledEvent;
import org.vinod.shared.events.ScreeningCompletedEvent;
import org.vinod.shared.events.UserRegisteredEvent;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class NotificationService {

    private final EmailService emailService;
    private final SmsService smsService;
    private final NotificationLogRepository logRepository;
    private final ProcessedEventRepository processedEventRepository;

    public NotificationService(EmailService emailService,
                               SmsService smsService,
                               NotificationLogRepository logRepository,
                               ProcessedEventRepository processedEventRepository) {
        this.emailService = emailService;
        this.smsService = smsService;
        this.logRepository = logRepository;
        this.processedEventRepository = processedEventRepository;
    }

    // ── Idempotency ───────────────────────────────────────────────────────

    /**
     * Returns true if an event with this key was already handled by the notification service.
     * Keys are prefixed with 'NS:' to avoid clashes with other services sharing the same table.
     */
    public boolean isAlreadyProcessed(String rawKey) {
        return processedEventRepository.existsByEventKey("NS:" + rawKey);
    }

    public void markProcessed(String rawKey, String eventType) {
        try {
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventKey("NS:" + rawKey)
                    .eventType(eventType)
                    .processedAt(LocalDateTime.now())
                    .build());
        } catch (DataIntegrityViolationException e) {
            // race-condition duplicate - safe to ignore
            log.debug("Duplicate processed-event key NS:{} - ignoring", rawKey);
        }
    }

    // ── Event handlers ────────────────────────────────────────────────────

    /**
     * Sends a welcome email when a new user registers.
     * The UserRegisteredEvent carries the recipient email directly.
     */
    public void handleUserRegistered(UserRegisteredEvent event) {
        String subject = "Welcome to Smart Hiring Assistant!";
        Map<String, Object> model = new HashMap<>();
        model.put("firstName", event.getFirstName() != null ? event.getFirstName() : event.getUsername());
        model.put("lastName", event.getLastName() != null ? event.getLastName() : "");
        model.put("username", event.getUsername());
        model.put("role", event.getRole());

        String status;
        String error = null;
        try {
            boolean sent = emailService.sendTemplatedEmail(event.getEmail(), subject, "welcome.ftl", model);
            status = sent ? "SENT" : "SKIPPED";
            if (!sent) {
                error = "Email delivery is disabled (notification.email.enabled=false)";
            }
        } catch (Exception e) {
            status = "FAILED";
            error = e.getMessage();
            log.warn("[NotificationService] Welcome email failed for userId={}: {}", event.getUserId(), e.getMessage());
        }

        logRepository.save(NotificationLog.builder()
                .eventType("UserRegisteredEvent")
                .recipientEmail(event.getEmail())
                .channel("EMAIL")
                .subject(subject)
                .status(status)
                .errorMessage(error)
                .referenceId("user:" + event.getUserId())
                .createdAt(LocalDateTime.now())
                .build());
    }

    /**
     * Records a candidate-matched notification.
     * The CandidateMatchedEvent does not carry an email address; the notification is logged
     * as SKIPPED until the event is enriched or a candidate-service lookup is added.
     */
    public void handleCandidateMatched(CandidateMatchedEvent event) {
        log.info("[NotificationService] CandidateMatched - candidateId={} jobId={} score={}",
                event.getCandidateId(), event.getJobId(), event.getMatchScore());

        // TODO: enrich with candidate email by calling candidate-service
        logRepository.save(NotificationLog.builder()
                .eventType("CandidateMatchedEvent")
                .channel("EMAIL")
                .subject("You have been matched to a job opportunity")
                .status("SKIPPED")
                .errorMessage("Recipient email not in event - enrich event or add candidate-service lookup")
                .referenceId("match:" + event.getMatchId())
                .createdAt(LocalDateTime.now())
                .build());
    }

    /**
     * Records a screening-completed notification.
     * The ScreeningCompletedEvent does not carry an email address.
     */
    public void handleScreeningCompleted(ScreeningCompletedEvent event) {
        String decisionLabel = "PASS".equalsIgnoreCase(event.getDecision()) ?
                "Congratulations - you passed screening!" :
                "Screening result for your application";
        log.info("[NotificationService] ScreeningCompleted - candidateId={} decision={}",
                event.getCandidateId(), event.getDecision());

        // TODO: enrich with candidate email by calling candidate-service
        logRepository.save(NotificationLog.builder()
                .eventType("ScreeningCompletedEvent")
                .channel("EMAIL")
                .subject(decisionLabel)
                .status("SKIPPED")
                .errorMessage("Recipient email not in event - enrich event or add candidate-service lookup")
                .referenceId("session:" + event.getSessionId())
                .createdAt(LocalDateTime.now())
                .build());
    }

    /**
     * Records an interview-scheduled notification.
     * The InterviewScheduledEvent does not carry an email address.
     */
    public void handleInterviewScheduled(InterviewScheduledEvent event) {
        log.info("[NotificationService] InterviewScheduled - candidateId={} interviewId={}",
                event.getCandidateId(), event.getInterviewId());

        // TODO: enrich with candidate email by calling candidate-service
        logRepository.save(NotificationLog.builder()
                .eventType("InterviewScheduledEvent")
                .channel("EMAIL")
                .subject("Your interview has been scheduled")
                .status("SKIPPED")
                .errorMessage("Recipient email not in event - enrich event or add candidate-service lookup")
                .referenceId("interview:" + event.getInterviewId())
                .createdAt(LocalDateTime.now())
                .build());
    }
}
