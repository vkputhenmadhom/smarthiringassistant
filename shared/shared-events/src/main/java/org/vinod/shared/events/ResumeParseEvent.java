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
public class ResumeParseEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("resume_id")
    private String resumeId;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("skills")
    private List<String> skills;

    @JsonProperty("experience_years")
    private Double experienceYears;

    @JsonProperty("location")
    private String location;

    /** Places the candidate is open to working from (cities or "Remote"); may be empty */
    @JsonProperty("preferred_locations")
    private List<String> preferredLocations;

    @JsonProperty("parsed_at")
    private long parsedAt;

    @JsonProperty("status")
    private String status; // SUCCESS, FAILURE

    @JsonProperty("error_message")
    private String errorMessage;
}

