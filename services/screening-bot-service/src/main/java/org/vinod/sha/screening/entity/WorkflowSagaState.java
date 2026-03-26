package org.vinod.sha.screening.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "workflow_saga_state")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowSagaState {

    @Id
    private String id;

    private String sagaId;
    private String workflowType;
    private String aggregateId;
    private String state;
    private List<String> stepsCompleted;
    private List<String> failureReasons;
    private LocalDateTime startedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime endedAt;
}

