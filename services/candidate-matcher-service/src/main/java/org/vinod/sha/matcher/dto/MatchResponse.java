package org.vinod.sha.matcher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchResponse {
    private String matchId;
    private Long candidateId;
    private String jobId;
    private Double overallScore;
    private Double skillMatchPercentage;
    private Double experienceMatchPercentage;
    private Double locationMatchPercentage;
    private String status;
    private String createdAt;
}

