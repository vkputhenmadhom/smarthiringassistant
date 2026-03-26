package org.vinod.smarthiringassistant.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Event published when a candidate interview is scheduled
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class InterviewScheduledEvent extends DomainEvent {
    
    @JsonProperty("interviewId")
    private String interviewId;
    
    @JsonProperty("candidateId")
    private String candidateId;
    
    @JsonProperty("jobId")
    private String jobId;
    
    @JsonProperty("interviewType")
    private String interviewType;
    
    @JsonProperty("scheduledDateTime")
    private LocalDateTime scheduledDateTime;
    
    @JsonProperty("duration")
    private Integer duration;
    
    @JsonProperty("interviewerName")
    private String interviewerName;
    
    @JsonProperty("location")
    private String location;
}

