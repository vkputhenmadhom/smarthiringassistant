package org.vinod.sha.screening.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.vinod.sha.screening.entity.WorkflowSagaState;

import java.util.Optional;

public interface WorkflowSagaStateRepository extends MongoRepository<WorkflowSagaState, String> {
    Optional<WorkflowSagaState> findBySagaId(String sagaId);
    Optional<WorkflowSagaState> findByAggregateId(String aggregateId);
}

