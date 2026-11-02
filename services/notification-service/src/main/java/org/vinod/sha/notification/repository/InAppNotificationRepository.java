package org.vinod.sha.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.vinod.sha.notification.entity.InAppNotification;

import java.util.List;
import java.util.Optional;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, Long> {

    List<InAppNotification> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    List<InAppNotification> findTop50ByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId);

    Optional<InAppNotification> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("update InAppNotification n set n.read = true where n.userId = :userId and n.read = false")
    int markAllRead(@Param("userId") Long userId);
}
