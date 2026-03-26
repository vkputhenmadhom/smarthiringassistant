package org.vinod.sha.analyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobAnalyzeRequest {
    private String jobId;
    private String jobTitle;
    private String companyName;
    private String location;
    private String employmentType;
    private String jobDescription;
}

