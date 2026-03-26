package org.vinod.smarthiringassistant.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Event published when a candidate is matched to a job
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class CandidateMatchedEvent extends DomainEvent {
    
    @JsonProperty("candidateId")
    private String candidateId;
    
    @JsonProperty("jobId")
    private String jobId;
    
    @JsonProperty("matchScore")
    private Float matchScore;
    
    @JsonProperty("matchStatus")
    private String matchStatus;
    
    @JsonProperty("matchedSkills")
    private java.util.List<String> matchedSkills;
    
    @JsonProperty("missingSkills")
    private java.util.List<String> missingSkills;
    
    @JsonProperty("matchedAt")
    private LocalDateTime matchedAt;
}

