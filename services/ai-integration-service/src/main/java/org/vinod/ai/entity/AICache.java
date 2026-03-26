package org.vinod.ai.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "ai_cache")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AICache {

    @Id
    private String id;

    @Indexed
    private String cacheKey; // Hash of prompt + parameters

    private String prompt;
    private String response;
    private String model;
    private Map<String, Object> parameters;

    @Indexed(expireAfter = "PT1H") // 1 hour TTL
    private LocalDateTime createdAt;

    @Indexed
    private LocalDateTime expiresAt;

    private long tokensUsed;
    private double cost;

}

