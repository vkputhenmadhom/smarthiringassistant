package org.vinod.sha.resumeparser.entity;

import jakarta.persistence.*;
import lombok.*;
import org.vinod.sha.resumeparser.security.EncryptedByteArrayConverter;

import java.time.LocalDateTime;

@Entity
@Table(name = "resumes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String resumeId;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false, columnDefinition = "BYTEA")
    @Convert(converter = EncryptedByteArrayConverter.class)
    private byte[] fileContent;

    @Column(nullable = false)
    private String fileFormat; // PDF, DOCX, TXT

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParseStatus status; // PENDING, PROCESSING, SUCCESS, FAILED

    @Column
    private String errorMessage;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "parsed_data_id")
    private ParsedResumeData parsedData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "parsed_at")
    private LocalDateTime parsedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        status = ParseStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

