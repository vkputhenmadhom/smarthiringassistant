package org.vinod.sha.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.vinod.sha.notification.entity.NotificationLog;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findByReferenceId(String referenceId);
    Page<NotificationLog> findByEventType(String eventType, Pageable pageable);
    Page<NotificationLog> findByStatus(String status, Pageable pageable);
}
