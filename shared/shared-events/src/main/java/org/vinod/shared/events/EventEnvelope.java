package org.vinod.shared.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventEnvelope<T> implements Serializable {
    private EventMetadata metadata;
    private T payload;
}

