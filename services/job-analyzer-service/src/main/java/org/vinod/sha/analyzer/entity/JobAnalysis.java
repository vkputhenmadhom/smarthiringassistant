package org.vinod.sha.analyzer.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "job_analysis")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobAnalysis {

    @Id
    private String id;

    private String jobId;
    private String jobTitle;
    private String companyName;
    private String department;
    private String location;
    private String employmentType;

    private String jobDescription;
    private List<String> requiredSkills;
    private List<String> preferredSkills;

    private Integer minExperienceYears;
    private Integer maxExperienceYears;

    private Integer salaryMin;
    private Integer salaryMax;
    private String currency;

    private Double confidence;
    private String status;
    private String notes;
    private Integer applicantCount;

    /** "INTERNAL" | "JSEARCH" | "ADZUNA" — set only for externally-synced jobs */
    private String source;
    /** Unique external job id used for deduplication, e.g. "JSEARCH:{job_id}" */
    private String externalId;
    /** Link back to the original job posting on the source portal */
    private String externalUrl;
    /** Company logo URL (populated by JSearch) */
    private String companyLogo;

    private LocalDateTime postedAt;
    private LocalDateTime closingDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
