package org.vinod.sha.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vinod.shared.events.UserRegisteredEvent;
import org.vinod.sha.notification.entity.NotificationLog;
import org.vinod.sha.notification.repository.NotificationLogRepository;

import java.time.Instant;

@Service
public class NotificationService {

    private final NotificationLogRepository notificationLogRepository;
    // Spring Boot 4 auto-configures Jackson 3 only, so there is no Jackson 2 ObjectMapper bean to inject.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public NotificationService(NotificationLogRepository notificationLogRepository) {
        this.notificationLogRepository = notificationLogRepository;
    }

    public boolean isAlreadyProcessed(String referenceId) {
        return referenceId != null && !referenceId.isBlank() && notificationLogRepository.existsByReferenceId(referenceId);
    }

    @Transactional
    public void handleUserRegistered(UserRegisteredEvent event) {
        if (event == null) {
            return;
        }

        String referenceId = "user:" + event.getUserId();
        if (isAlreadyProcessed(referenceId)) {
            return;
        }

        NotificationLog notificationLog = NotificationLog.builder()
                .referenceId(referenceId)
                .eventType("UserRegisteredEvent")
                .status("PROCESSED")
                .payload(toJson(event))
                .createdAt(Instant.now())
                .build();
        notificationLogRepository.save(notificationLog);
    }

    @Transactional
    public void markProcessed(String referenceId, String eventType) {
        if (referenceId == null || referenceId.isBlank()) {
            return;
        }
        if (notificationLogRepository.existsByReferenceId(referenceId)) {
            return;
        }

        NotificationLog notificationLog = NotificationLog.builder()
                .referenceId(referenceId)
                .eventType(eventType == null ? "UNKNOWN" : eventType)
                .status("PROCESSED")
                .payload("{}")
                .createdAt(Instant.now())
                .build();
        notificationLogRepository.save(notificationLog);
    }

    private String toJson(UserRegisteredEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            return event.toString();
        }
    }
}
