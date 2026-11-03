package org.vinod.sha.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_logs", schema = "hiring")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Column(name = "recipient_phone", length = 50)
    private String recipientPhone;

    /** EMAIL or SMS */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "subject", length = 500)
    private String subject;

    /** SENT | FAILED | SKIPPED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Domain reference, e.g. user:42, match:abc-123, session:xyz */
    @Column(name = "reference_id", length = 255)
    private String referenceId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
