package org.vinod.smarthiringassistant.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Base event class for all domain events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainEvent implements Serializable {
    
    @JsonProperty("eventId")
    private String eventId;
    
    @JsonProperty("eventType")
    private String eventType;
    
    @JsonProperty("aggregateId")
    private String aggregateId;
    
    @JsonProperty("aggregateType")
    private String aggregateType;
    
    @JsonProperty("occurredAt")
    private LocalDateTime occurredAt;
    
    @JsonProperty("source")
    private String source;
    
    @JsonProperty("correlationId")
    private String correlationId;
    
    @JsonProperty("version")
    private int version;
}

