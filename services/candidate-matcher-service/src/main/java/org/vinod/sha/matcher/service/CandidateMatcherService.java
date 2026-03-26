package org.vinod.sha.matcher.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vinod.sha.matcher.outbox.OutboxPublisher;
import org.vinod.sha.matcher.algorithm.WeightedScoreMatcher;
import org.vinod.sha.matcher.entity.CandidateMatch;
import org.vinod.sha.matcher.entity.MatchStatus;
import org.vinod.sha.matcher.repository.CandidateMatchRepository;
import org.vinod.sha.matcher.repository.JobRequirementRepository;
import org.vinod.shared.events.CandidateMatchedEvent;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CandidateMatcherService {

    private final CandidateMatchRepository candidateMatchRepository;
    private final JobRequirementRepository jobRequirementRepository;
    private final WeightedScoreMatcher weightedScoreMatcher;
    private final OutboxPublisher outboxPublisher;

    private static final String MATCHER_EXCHANGE = "matcher.exchange";
    private static final String CANDIDATE_MATCHED_ROUTING_KEY = "candidate.matched";

    /**
     * Match a candidate against all active jobs
     */
    public List<CandidateMatch> matchCandidateWithJobs(
            Long candidateId,
            List<String> candidateSkills,
            Double candidateExperience,
            String candidateLocation) {

        log.info("Matching candidate: {} against all active jobs", candidateId);

        var activeJobs = jobRequirementRepository.findAll().stream()
                .filter(job -> job.getStatus().toString().equals("ACTIVE"))
                .collect(Collectors.toList());

        List<CandidateMatch> matches = activeJobs.stream()
                .map(job -> {
                    // Calculate match score
                    double score = weightedScoreMatcher.calculateWeightedScore(
                            candidateSkills,
                            job.getRequiredSkills(),
                            candidateExperience,
                            job.getRequiredYearsExperience(),
                            candidateLocation,
                            job.getLocation()
                    );

                    // Only create match if score is above threshold (e.g., 30%)
                    if (score >= 30.0) {
                        return createMatch(candidateId, job.getJobId(), score, candidateSkills, 
                                         job.getRequiredSkills(), candidateLocation, job.getLocation());
                    }
                    return null;
                })
                .filter(match -> match != null)
                .sorted((m1, m2) -> Double.compare(m2.getOverallScore(), m1.getOverallScore()))
                .collect(Collectors.toList());

        log.info("Found {} matches for candidate: {}", matches.size(), candidateId);
        return matches;
    }

    /**
     * Match a specific candidate with a specific job
     */
    public CandidateMatch matchCandidateWithJob(
            Long candidateId,
            String jobId,
            List<String> candidateSkills,
            Double candidateExperience,
            String candidateLocation) {

        log.info("Matching candidate: {} with job: {}", candidateId, jobId);

        var job = jobRequirementRepository.findByJobId(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        double score = weightedScoreMatcher.calculateWeightedScore(
                candidateSkills,
                job.getRequiredSkills(),
                candidateExperience,
                job.getRequiredYearsExperience(),
                candidateLocation,
                job.getLocation()
        );

        CandidateMatch match = createMatch(candidateId, jobId, score, candidateSkills,
                                          job.getRequiredSkills(), candidateLocation, job.getLocation());

        log.info("Created match with score: {} for candidate: {} and job: {}", score, candidateId, jobId);
        return match;
    }

    /**
     * Get matches for a job (ranked by score)
     */
    public List<CandidateMatch> getMatchesForJob(String jobId) {
        return candidateMatchRepository.findByJobId(jobId).stream()
                .sorted((m1, m2) -> Double.compare(m2.getOverallScore(), m1.getOverallScore()))
                .collect(Collectors.toList());
    }

    /**
     * Get matches for a candidate (ranked by score)
     */
    public List<CandidateMatch> getMatchesForCandidate(Long candidateId) {
        return candidateMatchRepository.findByCandidateId(candidateId).stream()
                .sorted((m1, m2) -> Double.compare(m2.getOverallScore(), m1.getOverallScore()))
                .collect(Collectors.toList());
    }

    /**
     * Get a specific match
     */
    public CandidateMatch getMatch(String matchId) {
        return candidateMatchRepository.findByMatchId(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found: " + matchId));
    }

    /**
     * Update match status (e.g., REVIEWED, ACCEPTED, REJECTED)
     */
    public CandidateMatch updateMatchStatus(String matchId, MatchStatus status, String comments) {
        CandidateMatch match = getMatch(matchId);
        match.setStatus(status);
        match.setReviewerComments(comments);
        match = candidateMatchRepository.save(match);

        log.info("Updated match: {} status to: {}", matchId, status);
        
        // Publish event
        publishMatchEvent(match);
        
        return match;
    }

    private CandidateMatch createMatch(
            Long candidateId,
            String jobId,
            double overallScore,
            List<String> candidateSkills,
            List<String> requiredSkills,
            String candidateLocation,
            String jobLocation) {

        String matchId = UUID.randomUUID().toString();

        // Calculate individual scores
        double skillScore = calculateSkillMatchPercentage(candidateSkills, requiredSkills);
        double experienceScore = 50.0; // Placeholder
        double locationScore = calculateLocationMatchPercentage(candidateLocation, jobLocation);

        CandidateMatch match = CandidateMatch.builder()
                .matchId(matchId)
                .candidateId(candidateId)
                .jobId(jobId)
                .overallScore(overallScore)
                .skillMatchPercentage(skillScore)
                .experienceMatchPercentage(experienceScore)
                .locationMatchPercentage(locationScore)
                .build();

        match = candidateMatchRepository.save(match);

        // Publish event
        publishMatchEvent(match);

        return match;
    }

    private double calculateSkillMatchPercentage(List<String> candidateSkills, List<String> requiredSkills) {
        if (candidateSkills == null || requiredSkills == null || requiredSkills.isEmpty()) {
            return 0.0;
        }

        long matchCount = candidateSkills.stream()
                .filter(skill -> requiredSkills.stream()
                        .anyMatch(req -> req.toLowerCase().contains(skill.toLowerCase())))
                .count();

        return (matchCount * 100.0) / requiredSkills.size();
    }

    private double calculateLocationMatchPercentage(String candidateLocation, String jobLocation) {
        if (candidateLocation == null || jobLocation == null) {
            return 0.0;
        }

        if (candidateLocation.equalsIgnoreCase(jobLocation)) {
            return 100.0;
        }

        if (candidateLocation.toLowerCase().contains("remote") || 
            jobLocation.toLowerCase().contains("remote")) {
            return 100.0;
        }

        return 0.0;
    }

    private void publishMatchEvent(CandidateMatch match) {
        CandidateMatchedEvent event = CandidateMatchedEvent.builder()
                .matchId(match.getMatchId())
                .candidateId(match.getCandidateId())
                .jobId(match.getJobId())
                .matchScore(match.getOverallScore())
                .skillMatchPercentage(match.getSkillMatchPercentage())
                .experienceMatchPercentage(match.getExperienceMatchPercentage())
                .matchedAt(System.currentTimeMillis())
                .status(match.getStatus().toString())
                .build();

        try {
            outboxPublisher.enqueue(MATCHER_EXCHANGE, CANDIDATE_MATCHED_ROUTING_KEY, "CandidateMatchedEvent", event);
            log.info("Queued CandidateMatchedEvent to outbox for match: {}", match.getMatchId());
        } catch (Exception e) {
            log.error("Failed to enqueue CandidateMatchedEvent for match: {}", match.getMatchId(), e);
        }
    }
}

