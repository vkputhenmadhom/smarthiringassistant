package org.vinod.sha.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.vinod.sha.notification.entity.ProcessedEvent;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
    boolean existsByEventKey(String eventKey);
}
