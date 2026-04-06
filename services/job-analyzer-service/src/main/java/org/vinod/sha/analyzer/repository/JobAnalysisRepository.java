package org.vinod.sha.analyzer.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.vinod.sha.analyzer.entity.JobAnalysis;

@Repository
public interface JobAnalysisRepository extends MongoRepository<JobAnalysis, String> {
}

