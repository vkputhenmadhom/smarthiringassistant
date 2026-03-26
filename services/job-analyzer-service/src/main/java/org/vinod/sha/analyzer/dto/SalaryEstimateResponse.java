package org.vinod.sha.analyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryEstimateResponse {
    private Integer min;
    private Integer max;
    private String currency;
    private Double confidence;
    private String rationale;
}

