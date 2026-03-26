package org.vinod.sha.analyzer.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.vinod.sha.analyzer.entity.JobAnalysis;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobAnalysisRepository extends MongoRepository<JobAnalysis, String> {
    Optional<JobAnalysis> findByJobId(String jobId);
    List<JobAnalysis> findByJobTitleContainingIgnoreCase(String jobTitle);
    List<JobAnalysis> findByLocationContainingIgnoreCase(String location);
}

