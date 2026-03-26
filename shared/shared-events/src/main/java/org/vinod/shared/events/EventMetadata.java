package org.vinod.shared.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventMetadata implements Serializable {
    private String eventId;
    private String eventType;
    private String schemaVersion;
    private String sourceService;
    private long occurredAt;
    private String correlationId;
    private String causationId;
    private Map<String, String> attributes;
}

