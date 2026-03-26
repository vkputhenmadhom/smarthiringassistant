package org.vinod.sha.analyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryEstimateRequest {
    private String jobTitle;
    private String location;
    private Integer experienceYears;
    private List<String> requiredSkills;
}

