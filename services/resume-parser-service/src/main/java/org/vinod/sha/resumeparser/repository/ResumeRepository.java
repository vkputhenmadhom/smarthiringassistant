package org.vinod.sha.resumeparser.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.vinod.sha.resumeparser.entity.Resume;
import org.vinod.sha.resumeparser.entity.ParseStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, Long> {
    Optional<Resume> findByResumeId(String resumeId);
    List<Resume> findByUserId(Long userId);
    List<Resume> findByUserIdAndStatus(Long userId, ParseStatus status);
    List<Resume> findByStatus(ParseStatus status);

    /** Each user's most recent resume, newest first: one row per candidate. */
    @Query(value = "select r from Resume r where r.userId is not null and r.createdAt = "
            + "(select max(r2.createdAt) from Resume r2 where r2.userId = r.userId) order by r.createdAt desc",
            countQuery = "select count(distinct r.userId) from Resume r where r.userId is not null")
    Page<Resume> findLatestPerUser(Pageable pageable);

    @Query("select r from Resume r where r.userId in :userIds and r.createdAt = "
            + "(select max(r2.createdAt) from Resume r2 where r2.userId = r.userId)")
    List<Resume> findLatestForUsers(@Param("userIds") Collection<Long> userIds);

    Optional<Resume> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
