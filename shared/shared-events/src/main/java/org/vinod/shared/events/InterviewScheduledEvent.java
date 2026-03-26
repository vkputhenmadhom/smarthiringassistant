package org.vinod.shared.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewScheduledEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("interview_id")
    private String interviewId;

    @JsonProperty("candidate_id")
    private Long candidateId;

    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("scheduled_at")
    private long scheduledAt;

    @JsonProperty("channel")
    private String channel;

    @JsonProperty("status")
    private String status;
}

