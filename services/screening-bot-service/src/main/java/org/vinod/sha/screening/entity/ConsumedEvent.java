package org.vinod.sha.screening.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "consumed_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsumedEvent {

    @Id
    private String id;

    private String eventKey;
    private String eventType;
    private LocalDateTime consumedAt;
}

