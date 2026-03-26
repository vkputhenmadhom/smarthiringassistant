package org.vinod.sha.matcher.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "candidate_matches")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String matchId;

    @Column(nullable = false)
    private Long candidateId;

    @Column(nullable = false)
    private String jobId;

    @Column(nullable = false)
    private Double overallScore;

    @Column(nullable = false)
    private Double skillMatchPercentage;

    @Column(nullable = false)
    private Double experienceMatchPercentage;

    @Column(nullable = false)
    private Double locationMatchPercentage;

    @Enumerated(EnumType.STRING)
    private MatchStatus status; // NEW, REVIEWED, ACCEPTED, REJECTED

    @Column
    private String reviewerComments;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        status = MatchStatus.NEW;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

