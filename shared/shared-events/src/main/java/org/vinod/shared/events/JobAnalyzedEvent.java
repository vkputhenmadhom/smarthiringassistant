package org.vinod.shared.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobAnalyzedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("analysis_id")
    private String analysisId;

    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("job_title")
    private String jobTitle;

    @JsonProperty("required_skills")
    private List<String> requiredSkills;

    @JsonProperty("salary_min")
    private Integer salaryMin;

    @JsonProperty("salary_max")
    private Integer salaryMax;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("confidence")
    private Double confidence;

    @JsonProperty("analyzed_at")
    private long analyzedAt;
}

