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
public class ScreeningCompensationRequestedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("candidate_id")
    private Long candidateId;

    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("failure_reasons")
    private List<String> failureReasons;

    @JsonProperty("requested_at")
    private long requestedAt;
}

