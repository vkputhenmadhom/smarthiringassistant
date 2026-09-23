package org.vinod.shared.analytics.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsEvent implements Serializable {
    private String eventId;
    private String eventType;
    private Integer eventVersion;
    private Instant eventTime;
    private String sourceService;
    private String correlationId;
    private String candidateId;
    private String jobId;
    private String userId;
    private String sessionId;
    private String portal;
    private String status;
    private String stage;
    @Builder.Default
    private Map<String, Object> attributes = new LinkedHashMap<>();
}
