package org.vinod.sha.matcher.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vinod.sha.matcher.entity.JobRequirement;
import org.vinod.sha.matcher.entity.JobStatus;
import org.vinod.sha.matcher.entity.CandidateMatch;
import org.vinod.sha.matcher.entity.MatchStatus;
import org.vinod.sha.matcher.repository.CandidateMatchRepository;
import org.vinod.sha.matcher.repository.JobRequirementRepository;
import org.vinod.shared.events.JobPostingChangedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Keeps candidate-matcher's copy of job postings in line with job-analyzer-service, which owns them.
 * Applying the same event twice is harmless (upsert by job id; deleting a missing job is a no-op).
 */
@Slf4j
@Service
public class JobRequirementSyncService {

    private final JobRequirementRepository repository;
    private final CandidateMatchRepository matches;

    public JobRequirementSyncService(JobRequirementRepository repository, CandidateMatchRepository matches) {
        this.repository = repository;
        this.matches = matches;
    }

    @Transactional
    public void apply(JobPostingChangedEvent event) {
        if (event.getJobId() == null || event.getJobId().isBlank()) {
            log.warn("Ignoring JobPostingChangedEvent without a job id");
            return;
        }
        if (event.isDeleted()) {
            repository.findByJobId(event.getJobId()).ifPresent(job -> {
                repository.delete(job);
                log.info("Removed job {} from matching", event.getJobId());
            });
            dropUntouchedMatches(event.getJobId());
            return;
        }

        JobRequirement job = repository.findByJobId(event.getJobId()).orElseGet(JobRequirement::new);
        job.setJobId(event.getJobId());
        job.setJobTitle(fit(defaultIfBlank(event.getJobTitle(), "Untitled job")));
        job.setCompanyName(fit(defaultIfBlank(event.getCompanyName(), "Unknown company")));
        job.setLocation(fit(event.getLocation()));
        job.setJobDescription(event.getJobDescription());
        job.setRequiredSkills(event.getRequiredSkills() == null ? new ArrayList<>() : new ArrayList<>(event.getRequiredSkills()));
        job.setRequiredYearsExperience(event.getMinExperienceYears() == null ? 0 : Math.max(0, event.getMinExperienceYears()));
        job.setMaxYearsExperience(event.getMaxExperienceYears());
        // Outside the job market (e.g. a US job while the app serves India): keep it, but never match it.
        job.setStatus(Boolean.FALSE.equals(event.getInMarket()) ? JobStatus.ON_HOLD : toMatcherStatus(event.getStatus()));
        repository.save(job);
        if (job.getStatus() != JobStatus.ACTIVE) {
            dropUntouchedMatches(job.getJobId());
        }
    }

    /** A job that closes or leaves the market stops being recommended; matches HR acted on stay. */
    private void dropUntouchedMatches(String jobId) {
        List<CandidateMatch> untouched = matches.findByJobIdAndStatus(jobId, MatchStatus.MATCHED);
        if (!untouched.isEmpty()) {
            matches.deleteAll(untouched);
            log.info("Removed {} untouched matches for job {}, which is no longer open", untouched.size(), jobId);
        }
    }

    /** Only OPEN jobs are matched; drafts and analyses wait, closed jobs stop matching. */
    static JobStatus toMatcherStatus(String jobAnalyzerStatus) {
        String status = jobAnalyzerStatus == null ? "" : jobAnalyzerStatus.toUpperCase(Locale.ROOT);
        return switch (status) {
            case "OPEN", "PUBLISHED", "ACTIVE" -> JobStatus.ACTIVE;
            case "CLOSED", "ARCHIVED" -> JobStatus.CLOSED;
            default -> JobStatus.ON_HOLD;
        };
    }

    /** Title, company and location are VARCHAR(255); job-board data is not guaranteed to fit. */
    private static String fit(String value) {
        return value == null || value.length() <= 255 ? value : value.substring(0, 255);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
