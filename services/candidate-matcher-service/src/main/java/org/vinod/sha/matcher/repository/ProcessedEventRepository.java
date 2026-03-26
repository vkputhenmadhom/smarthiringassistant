package org.vinod.sha.matcher.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vinod.sha.matcher.entity.ProcessedEvent;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
    boolean existsByEventKey(String eventKey);
}

