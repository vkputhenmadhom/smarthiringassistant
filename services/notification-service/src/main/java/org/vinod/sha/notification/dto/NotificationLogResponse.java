package org.vinod.sha.notification.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLogResponse {
    private Long id;
    private String eventType;
    private String recipientEmail;
    private String channel;
    private String subject;
    private String status;
    private String errorMessage;
    private String referenceId;
    private LocalDateTime createdAt;
}
