package org.vinod.sha.matcher.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vinod.sha.matcher.outbox.OutboxPublisher;
import org.vinod.sha.matcher.algorithm.WeightedScoreMatcher;
import org.vinod.sha.matcher.entity.CandidateMatch;
import org.vinod.sha.matcher.entity.JobRequirement;
import org.vinod.sha.matcher.entity.JobStatus;
import org.vinod.sha.matcher.entity.MatchStatus;
import org.vinod.sha.matcher.repository.CandidateMatchRepository;
import org.vinod.sha.matcher.repository.JobRequirementRepository;
import org.vinod.shared.events.CandidateMatchedEvent;
import org.vinod.shared.text.SkillText;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class CandidateMatcherService {

    private final CandidateMatchRepository candidateMatchRepository;
    private final JobRequirementRepository jobRequirementRepository;
    private final WeightedScoreMatcher weightedScoreMatcher;
    private final OutboxPublisher outboxPublisher;
    private final double minScore;
    private final int maxMatchesPerResume;

    public CandidateMatcherService(CandidateMatchRepository candidateMatchRepository,
                                   JobRequirementRepository jobRequirementRepository,
                                   WeightedScoreMatcher weightedScoreMatcher,
                                   OutboxPublisher outboxPublisher,
                                   @Value("${matching.min-score:50}") double minScore,
                                   @Value("${matching.max-matches-per-resume:20}") int maxMatchesPerResume) {
        this.candidateMatchRepository = candidateMatchRepository;
        this.jobRequirementRepository = jobRequirementRepository;
        this.weightedScoreMatcher = weightedScoreMatcher;
        this.outboxPublisher = outboxPublisher;
        this.minScore = minScore;
        this.maxMatchesPerResume = maxMatchesPerResume;
    }

    private static final String MATCHER_EXCHANGE = "matcher.exchange";
    private static final String CANDIDATE_MATCHED_ROUTING_KEY = "candidate.matched";

    /**
     * Match a candidate against all active jobs. Only jobs where the candidate has a relevant skill and
     * scores at least {@code matching.min-score} are kept, best first, at most
     * {@code matching.max-matches-per-resume} of them.
     * <p>
     * A new resume replaces the candidate's earlier automatic matches. Matches HR has acted on (shortlisted,
     * rejected, hired) stay, and their jobs are not matched again.
     */
    public List<CandidateMatch> matchCandidateWithJobs(
            Long candidateId,
            List<String> candidateSkills,
            Double candidateExperience,
            String candidateLocation) {

        log.info("Matching candidate: {} against all active jobs", candidateId);

        var activeJobs = jobRequirementRepository.findAll().stream()
                .filter(job -> job.getStatus() == JobStatus.ACTIVE)
                .toList();

        List<CandidateMatch> previous = candidateMatchRepository.findByCandidateId(candidateId);
        List<CandidateMatch> replaced = previous.stream().filter(CandidateMatcherService::untouched).toList();
        candidateMatchRepository.deleteAll(replaced);
        Set<String> keptJobs = previous.stream()
                .filter(m -> !untouched(m))
                .map(CandidateMatch::getJobId)
                .collect(Collectors.toSet());
        if (!replaced.isEmpty()) {
            log.info("Replacing {} earlier matches for candidate: {} (keeping {} HR has acted on)", replaced.size(), candidateId, keptJobs.size());
        }

        record Scored(JobRequirement job, WeightedScoreMatcher.Score score) {}

        List<CandidateMatch> matches = activeJobs.stream()
                .filter(job -> !keptJobs.contains(job.getJobId()))
                .map(job -> new Scored(job, score(job, candidateSkills, candidateExperience, candidateLocation)))
                .filter(s -> s.score().hasRelevantSkill() && s.score().overall() >= minScore)
                .sorted(Comparator.comparingDouble((Scored s) -> s.score().overall()).reversed())
                .limit(maxMatchesPerResume)
                .map(s -> createMatch(candidateId, s.job(), s.score(), candidateSkills))
                .toList();

        log.info("Found {} matches for candidate: {} among {} active jobs", matches.size(), candidateId, activeJobs.size());
        return matches;
    }

    /** An automatic match nobody has acted on yet */
    private static boolean untouched(CandidateMatch match) {
        return match.getStatus() == null || match.getStatus() == MatchStatus.MATCHED || match.getStatus() == MatchStatus.PENDING;
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

        var score = score(job, candidateSkills, candidateExperience, candidateLocation);
        CandidateMatch match = createMatch(candidateId, job, score, candidateSkills);

        log.info("Created match with score: {} for candidate: {} and job: {}", score.overall(), candidateId, jobId);
        return match;
    }

    private WeightedScoreMatcher.Score score(JobRequirement job, List<String> candidateSkills,
                                             Double candidateExperience, String candidateLocation) {
        return weightedScoreMatcher.score(
                candidateSkills,
                job.getRequiredSkills(),
                job.getJobTitle(),
                job.getJobDescription(),
                candidateExperience,
                job.getRequiredYearsExperience(),
                candidateLocation,
                job.getLocation());
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
        return findMatch(matchId).orElseThrow(() -> new RuntimeException("Match not found: " + matchId));
    }

    /** Each candidate's best match score (0–100); candidates without matches are left out. */
    public Map<Long, Double> bestScores(Collection<Long> candidateIds) {
        Map<Long, Double> best = new LinkedHashMap<>();
        if (!candidateIds.isEmpty()) {
            candidateMatchRepository.findBestScores(candidateIds)
                    .forEach(row -> best.put(((Number) row[0]).longValue(), ((Number) row[1]).doubleValue()));
        }
        return best;
    }

    public Optional<CandidateMatch> findMatch(String matchId) {
        return candidateMatchRepository.findByMatchId(matchId);
    }

    /**
     * Move a match along the pipeline (e.g. SHORTLISTED, REJECTED, HIRED)
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

    /** Experience and location stay null when the job or candidate leaves them unknown. */
    private CandidateMatch createMatch(
            Long candidateId,
            JobRequirement job,
            WeightedScoreMatcher.Score score,
            List<String> candidateSkills) {

        List<String> requiredSkills = job.getRequiredSkills();
        boolean listsSkills = requiredSkills != null && !requiredSkills.isEmpty();

        CandidateMatch match = CandidateMatch.builder()
                .matchId(UUID.randomUUID().toString())
                .candidateId(candidateId)
                .jobId(job.getJobId())
                .overallScore(score.overall())
                .skillMatchPercentage(score.skills())
                .experienceMatchPercentage(score.experience())
                .locationMatchPercentage(score.location())
                .matchedSkills(listsSkills
                        ? skillsIn(requiredSkills, candidateSkills, true)
                        : mentionedIn(job, candidateSkills))
                .missingSkills(skillsIn(requiredSkills, candidateSkills, false))
                .build();

        match = candidateMatchRepository.save(match);

        // Publish event
        publishMatchEvent(match);

        return match;
    }

    /** The required skills the candidate has ({@code present}) or lacks, compared case-insensitively. */
    static List<String> skillsIn(List<String> requiredSkills, List<String> candidateSkills, boolean present) {
        if (requiredSkills == null) {
            return List.of();
        }
        Set<String> candidate = candidateSkills == null ? Set.of() : candidateSkills.stream()
                .filter(Objects::nonNull)
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return requiredSkills.stream()
                .filter(Objects::nonNull)
                .filter(skill -> candidate.contains(skill.trim().toLowerCase(Locale.ROOT)) == present)
                .distinct()
                .toList();
    }

    /** For a job that lists no skills: the candidate's skills its title or description mentions. */
    private static List<String> mentionedIn(JobRequirement job, List<String> candidateSkills) {
        String text = (job.getJobTitle() == null ? "" : job.getJobTitle()) + "\n"
                + (job.getJobDescription() == null ? "" : job.getJobDescription());
        return SkillText.find(text, candidateSkills);
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

