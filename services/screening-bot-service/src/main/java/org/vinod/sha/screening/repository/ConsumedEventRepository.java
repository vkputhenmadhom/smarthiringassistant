package org.vinod.sha.screening.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.vinod.sha.screening.entity.ConsumedEvent;

public interface ConsumedEventRepository extends MongoRepository<ConsumedEvent, String> {
    boolean existsByEventKey(String eventKey);
}

