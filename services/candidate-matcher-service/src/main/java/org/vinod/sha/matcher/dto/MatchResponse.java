package org.vinod.sha.matcher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
    private List<String> matchedSkills;
    private List<String> missingSkills;
    private String createdAt;
}

