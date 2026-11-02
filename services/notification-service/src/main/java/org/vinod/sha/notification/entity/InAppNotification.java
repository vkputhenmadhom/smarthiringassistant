package org.vinod.sha.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A message shown in a user's notification bell in the app. */
@Entity
@Table(name = "in_app_notifications", schema = "hiring")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InAppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** e.g. SHORTLISTED, HIRED */
    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    /** Domain reference, e.g. match:abc-123 */
    @Column(name = "reference_id", length = 255)
    private String referenceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
