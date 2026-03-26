package org.vinod.sha.matcher.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.vinod.sha.matcher.entity.JobRequirement;
import org.vinod.sha.matcher.entity.JobStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobRequirementRepository extends JpaRepository<JobRequirement, Long> {
    Optional<JobRequirement> findByJobId(String jobId);
    List<JobRequirement> findByStatus(JobStatus status);
    List<JobRequirement> findByLocationContainingIgnoreCase(String location);
}

