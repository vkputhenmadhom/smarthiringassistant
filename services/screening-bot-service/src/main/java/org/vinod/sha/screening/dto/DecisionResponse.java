package org.vinod.sha.screening.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionResponse {
    private String sessionId;
    private String decision;
    private Double finalScore;
    private List<String> failureReasons;
}

