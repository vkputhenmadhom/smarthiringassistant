package org.vinod.sha.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vinod.sha.notification.entity.NotificationLog;

import java.util.Optional;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    Optional<NotificationLog> findByReferenceId(String referenceId);
    boolean existsByReferenceId(String referenceId);
}
