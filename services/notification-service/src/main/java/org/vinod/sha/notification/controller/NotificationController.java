package org.vinod.sha.notification.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vinod.sha.notification.dto.NotificationLogResponse;
import org.vinod.sha.notification.dto.SendTestEmailRequest;
import org.vinod.sha.notification.entity.NotificationLog;
import org.vinod.sha.notification.repository.NotificationLogRepository;
import org.vinod.sha.notification.service.EmailService;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/")
public class NotificationController {

    private final NotificationLogRepository logRepository;
    private final EmailService emailService;

    public NotificationController(NotificationLogRepository logRepository,
                                  EmailService emailService) {
        this.logRepository = logRepository;
        this.emailService = emailService;
    }

    /** GET /api/notifications/logs - paginated list of all notification logs */
    @GetMapping("logs")
    public ResponseEntity<Page<NotificationLogResponse>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<NotificationLog> logs = logRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(logs.map(this::toResponse));
    }

    /** GET /api/notifications/logs/{id} - single log entry */
    @GetMapping("logs/{id}")
    public ResponseEntity<NotificationLogResponse> getLog(@PathVariable Long id) {
        return logRepository.findById(id)
                .map(log -> ResponseEntity.ok(toResponse(log)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** GET /api/notifications/logs/by-reference/{ref} - logs for a domain reference */
    @GetMapping("logs/by-reference/{ref}")
    public ResponseEntity<List<NotificationLogResponse>> getLogsByReference(
            @PathVariable String ref) {
        List<NotificationLogResponse> result = logRepository.findByReferenceId(ref)
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/notifications/send-test
     * Sends a plain-text test email. Intended for development/smoke-testing only.
     */
    @PostMapping("send-test")
    public ResponseEntity<Map<String, String>> sendTestEmail(
            @RequestBody SendTestEmailRequest request) {
        try {
            emailService.sendPlainEmail(request.getTo(), request.getSubject(), request.getBody());
            return ResponseEntity.ok(Map.of("status", "sent", "to", request.getTo()));
        } catch (Exception e) {
            log.error("Test email failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "failed", "error", e.getMessage()));
        }
    }

    /** GET /api/notifications/health - simple liveness check */
    @GetMapping("health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "notification-service"));
    }

    private NotificationLogResponse toResponse(NotificationLog log) {
        return NotificationLogResponse.builder()
                .id(log.getId())
                .eventType(log.getEventType())
                .recipientEmail(log.getRecipientEmail())
                .channel(log.getChannel())
                .subject(log.getSubject())
                .status(log.getStatus())
                .errorMessage(log.getErrorMessage())
                .referenceId(log.getReferenceId())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
