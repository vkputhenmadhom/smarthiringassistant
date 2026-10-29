package org.vinod.shared.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * A job posting was created, changed or deleted in job-analyzer-service (the owner of job data).
 * candidate-matcher-service keeps its own copy of the fields it matches on from these events.
 * {@code jobId} is the job's document id, the id the frontends and GraphQL API use.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPostingChangedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("job_id")
    private String jobId;

    /** True when the job was deleted; the other fields are then empty. */
    @JsonProperty("deleted")
    private boolean deleted;

    @JsonProperty("job_title")
    private String jobTitle;

    @JsonProperty("company_name")
    private String companyName;

    @JsonProperty("location")
    private String location;

    @JsonProperty("job_description")
    private String jobDescription;

    @JsonProperty("required_skills")
    private List<String> requiredSkills;

    @JsonProperty("min_experience_years")
    private Integer minExperienceYears;

    /** job-analyzer status: DRAFT, ANALYZED, OPEN or CLOSED. */
    @JsonProperty("status")
    private String status;

    @JsonProperty("changed_at")
    private long changedAt;
}
