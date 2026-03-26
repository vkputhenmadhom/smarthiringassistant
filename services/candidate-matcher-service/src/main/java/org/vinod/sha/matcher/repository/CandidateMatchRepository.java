package org.vinod.sha.matcher.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.vinod.sha.matcher.entity.CandidateMatch;
import org.vinod.sha.matcher.entity.MatchStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface CandidateMatchRepository extends JpaRepository<CandidateMatch, Long> {
    Optional<CandidateMatch> findByMatchId(String matchId);
    List<CandidateMatch> findByJobId(String jobId);
    List<CandidateMatch> findByCandidateId(Long candidateId);
    List<CandidateMatch> findByJobIdAndStatus(String jobId, MatchStatus status);
    List<CandidateMatch> findByCandidateIdAndStatus(Long candidateId, MatchStatus status);
}

