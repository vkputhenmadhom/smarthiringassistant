package org.vinod.sha.notification.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.vinod.sha.notification.config.NotificationRabbitConfig;
import org.vinod.sha.notification.service.NotificationService;
import org.vinod.shared.events.InterviewScheduledEvent;

@Slf4j
@Component
public class InterviewScheduledListener {

    private final NotificationService notificationService;

    public InterviewScheduledListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = NotificationRabbitConfig.INTERVIEW_SCHEDULED_NOTIFICATION_QUEUE)
    public void onInterviewScheduled(InterviewScheduledEvent event) {
        try {
            String key = "InterviewScheduledEvent:" + event.getInterviewId();
            if (notificationService.isAlreadyProcessed(key)) {
                log.info("[InterviewScheduledListener] Duplicate event skipped interviewId={}", event.getInterviewId());
                return;
            }
            log.info("[InterviewScheduledListener] Processing interviewId={} candidateId={}",
                    event.getInterviewId(), event.getCandidateId());
            notificationService.handleInterviewScheduled(event);
            notificationService.markProcessed(key, "InterviewScheduledEvent");
        } catch (Exception e) {
            log.error("[InterviewScheduledListener] Error processing event interviewId={}: {}",
                    event.getInterviewId(), e.getMessage(), e);
        }
    }
}
