package org.vinod.sha.screening.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.vinod.sha.screening.entity.ScreeningSession;

import java.util.List;

@Repository
public interface ScreeningSessionRepository extends MongoRepository<ScreeningSession, String> {
    List<ScreeningSession> findByCandidateId(Long candidateId);
    List<ScreeningSession> findByJobId(String jobId);
    List<ScreeningSession> findByDecision(String decision);
}

