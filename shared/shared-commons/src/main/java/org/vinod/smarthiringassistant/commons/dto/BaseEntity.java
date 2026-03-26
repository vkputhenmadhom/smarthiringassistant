package org.vinod.smarthiringassistant.commons.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Base entity DTO with common fields
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseEntity implements Serializable {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("createdBy")
    private String createdBy;
    
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
    
    @JsonProperty("updatedBy")
    private String updatedBy;
    
    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;
    
    @JsonProperty("isActive")
    private Boolean isActive;
}

