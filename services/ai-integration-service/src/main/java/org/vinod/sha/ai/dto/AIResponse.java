package org.vinod.sha.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AIResponse {
    private String response;
    /** The model that actually produced the response. */
    private String model;
    private Integer tokensUsed;
    private Boolean cached;
    private String error;
}
