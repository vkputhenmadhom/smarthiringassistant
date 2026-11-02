package org.vinod.sha.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vinod.sha.notification.config.InAppMessages;
import org.vinod.sha.notification.entity.InAppNotification;
import org.vinod.sha.notification.repository.InAppNotificationRepository;
import org.vinod.shared.events.CandidateMatchedEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Notifications shown in a user's bell in the app. */
@Slf4j
@Service
public class InAppNotificationService {

    private final InAppNotificationRepository repository;
    private final InAppMessages messages;

    public InAppNotificationService(InAppNotificationRepository repository, InAppMessages messages) {
        this.repository = repository;
        this.messages = messages;
    }

    /** Tells the candidate about a match status that has a configured message (e.g. shortlisted, hired). */
    @Transactional
    public Optional<InAppNotification> onMatchStatus(CandidateMatchedEvent event) {
        String status = event.getStatus() == null ? "" : event.getStatus().toUpperCase(Locale.ROOT);
        InAppMessages.Message template = messages.matchStatus().get(status);
        if (template == null || event.getCandidateId() == null) {
            return Optional.empty();
        }
        String job = jobLabel(event);
        InAppNotification saved = repository.save(InAppNotification.builder()
                .userId(event.getCandidateId())
                .type(status)
                .title(template.title().replace("{job}", job))
                .message(template.message() == null ? null : template.message().replace("{job}", job))
                .read(false)
                .referenceId("match:" + event.getMatchId())
                .createdAt(LocalDateTime.now())
                .build());
        log.info("[InApp] Told candidate {} that match {} is {}", event.getCandidateId(), event.getMatchId(), status);
        return Optional.of(saved);
    }

    public List<InAppNotification> forUser(Long userId, boolean unreadOnly) {
        return unreadOnly
                ? repository.findTop50ByUserIdAndReadFalseOrderByCreatedAtDesc(userId)
                : repository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public Optional<InAppNotification> markRead(Long id, Long userId) {
        return repository.findByIdAndUserId(id, userId).map(n -> {
            n.setRead(true);
            return repository.save(n);
        });
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllRead(userId);
    }

    /** "Principal Engineer at Acme", or "a job" when the event has no title */
    static String jobLabel(CandidateMatchedEvent event) {
        String title = event.getJobTitle() == null || event.getJobTitle().isBlank() ? "a job" : event.getJobTitle().trim();
        String company = event.getCompanyName() == null ? "" : event.getCompanyName().trim();
        return company.isEmpty() || company.equalsIgnoreCase("Unknown company") ? title : title + " at " + company;
    }
}
