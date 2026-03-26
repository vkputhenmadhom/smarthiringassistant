package org.vinod.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIRequest {
    private String userId;
    private String prompt;
    private String model;
    private Double temperature;
    private Integer maxTokens;
}

