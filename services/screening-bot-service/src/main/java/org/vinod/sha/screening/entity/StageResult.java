package org.vinod.sha.screening.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StageResult {
    private String stage;
    private boolean passed;
    private double score;
    private String response;
    private List<String> reasons;
    private LocalDateTime evaluatedAt;
}

