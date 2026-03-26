package org.vinod.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIResponse {
    private String response;
    private String model;
    private Integer tokensUsed;
    private Double cost;
    private String error;
}

