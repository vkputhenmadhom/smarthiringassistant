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
public class MatchRequest {
    private Long candidateId;
    private String jobId;
    private List<String> skills;
    private Double experienceYears;
    private String location;
}

