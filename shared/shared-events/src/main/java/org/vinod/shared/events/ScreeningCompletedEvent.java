package org.vinod.shared.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScreeningCompletedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("candidate_id")
    private Long candidateId;

    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("decision")
    private String decision;

    @JsonProperty("final_score")
    private Double finalScore;

    @JsonProperty("failure_reasons")
    private List<String> failureReasons;

    @JsonProperty("completed_at")
    private long completedAt;
}

