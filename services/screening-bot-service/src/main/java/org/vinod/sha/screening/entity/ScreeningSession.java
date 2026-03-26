package org.vinod.sha.screening.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "screening_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScreeningSession {

    @Id
    private String id;

    private Long candidateId;
    private String jobId;

    private String currentStage;
    private String status; // IN_PROGRESS, COMPLETED
    private String decision; // PASS, FAIL, PENDING

    private List<StageResult> stageResults;
    private List<String> failureReasons;

    private Double finalScore;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}

