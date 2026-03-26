package org.vinod.smarthiringassistant.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Event published when a resume is successfully parsed
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ResumeParsedEvent extends DomainEvent {
    
    @JsonProperty("resumeId")
    private String resumeId;
    
    @JsonProperty("candidateId")
    private String candidateId;
    
    @JsonProperty("candidateName")
    private String candidateName;
    
    @JsonProperty("candidateEmail")
    private String candidateEmail;
    
    @JsonProperty("skills")
    private java.util.List<String> skills;
    
    @JsonProperty("experience")
    private Float experienceYears;
    
    @JsonProperty("summary")
    private String summary;
    
    @JsonProperty("parseStatus")
    private String parseStatus;
    
    @JsonProperty("parsedAt")
    private LocalDateTime parsedAt;
}

