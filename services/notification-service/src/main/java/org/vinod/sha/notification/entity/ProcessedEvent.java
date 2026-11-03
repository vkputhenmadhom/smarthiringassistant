package org.vinod.sha.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Idempotency guard - maps to the shared hiring.processed_events table.
 * Event keys are prefixed with 'NS:' to avoid collisions with other services.
 */
@Entity
@Table(name = "processed_events", schema = "hiring")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_key", nullable = false, unique = true, length = 255)
    private String eventKey;

    @Column(name = "event_type", nullable = false, length = 255)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
