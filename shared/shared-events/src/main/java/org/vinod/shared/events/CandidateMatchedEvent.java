package org.vinod.shared.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateMatchedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("match_id")
    private String matchId;

    @JsonProperty("candidate_id")
    private Long candidateId;

    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("match_score")
    private Double matchScore;

    @JsonProperty("skill_match_percentage")
    private Double skillMatchPercentage;

    @JsonProperty("experience_match_percentage")
    private Double experienceMatchPercentage;

    @JsonProperty("matched_at")
    private long matchedAt;

    @JsonProperty("status")
    private String status; // NEW, REVIEWED, ACCEPTED, REJECTED
}

