package org.vinod.sha.resumeparser.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.vinod.sha.resumeparser.entity.Resume;
import org.vinod.sha.resumeparser.entity.ParseStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, Long> {
    Optional<Resume> findByResumeId(String resumeId);
    List<Resume> findByUserId(Long userId);
    List<Resume> findByUserIdAndStatus(Long userId, ParseStatus status);
    List<Resume> findByStatus(ParseStatus status);
}

