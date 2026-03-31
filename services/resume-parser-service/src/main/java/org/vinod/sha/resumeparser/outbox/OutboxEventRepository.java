package org.vinod.sha.resumeparser.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);
    List<OutboxEvent> findTop100ByStatusInOrderByCreatedAtAsc(List<OutboxStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e.id from OutboxEvent e where e.status in :statuses order by e.createdAt asc")
    List<Long> lockIdsByStatusInOrderByCreatedAtAsc(@Param("statuses") List<OutboxStatus> statuses, Pageable pageable);

    @Transactional
    @Modifying
    @Query("update OutboxEvent e set e.status = :toStatus, e.updatedAt = :updatedAt where e.id in :ids and e.status in :fromStatuses")
    int transitionStatusByIds(@Param("ids") List<Long> ids,
                              @Param("fromStatuses") List<OutboxStatus> fromStatuses,
                              @Param("toStatus") OutboxStatus toStatus,
                              @Param("updatedAt") LocalDateTime updatedAt);

    Optional<OutboxEvent> findByIdAndStatus(Long id, OutboxStatus status);
}

