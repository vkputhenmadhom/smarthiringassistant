package org.vinod.sha.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code model} and {@code temperature} are accepted for backward compatibility but ignored:
 * the model is chosen server-side, and current reasoning models reject custom temperatures.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIRequest {
    @NotBlank
    private String userId;
    @NotBlank
    private String prompt;
    private String model;
    private Double temperature;
    private Integer maxTokens;
}
