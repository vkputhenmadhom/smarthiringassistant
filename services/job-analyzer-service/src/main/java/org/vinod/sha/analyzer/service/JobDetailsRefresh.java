package org.vinod.sha.analyzer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.vinod.sha.analyzer.config.JobSyncProperties.Feed;
import org.vinod.sha.analyzer.entity.JobAnalysis;
import org.vinod.sha.analyzer.repository.JobAnalysisRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Keeps imported jobs in line with the configuration: at startup their skills and seniority are re-read with
 * job-skills.yml, and postings older than their feed's {@code max-age-days} (job-feeds.yml) are closed, then
 * again after each sync. Saving a job tells candidate-matcher; jobs that do not change are not saved.
 */
@Slf4j
@Component
public class JobDetailsRefresh implements ApplicationRunner {

    private final JobAnalysisRepository repository;
    private final JobSkills jobSkills;
    private final JobLevels jobLevels;
    private final JobFeeds jobFeeds;

    JobDetailsRefresh(JobAnalysisRepository repository, JobSkills jobSkills, JobLevels jobLevels, JobFeeds jobFeeds) {
        this.repository = repository;
        this.jobSkills = jobSkills;
        this.jobLevels = jobLevels;
        this.jobFeeds = jobFeeds;
    }

    @Override
    public void run(ApplicationArguments args) {
        int reread = 0;
        for (JobAnalysis job : repository.findAll()) {
            if (job.getSource() == null || job.getSource().isBlank()) {
                continue; // posted in the app: HR chose its skills and experience
            }
            List<String> skills = refreshed(job.getRequiredSkills(), job.getJobTitle(), job.getJobDescription());
            JobLevels.Seniority seniority = jobLevels.of(job.getJobTitle(), job.getJobDescription());
            boolean changed = !Objects.equals(skills, job.getRequiredSkills() == null ? List.of() : job.getRequiredSkills())
                    || !Objects.equals(seniority.level(), job.getSeniority())
                    || !Objects.equals(seniority.minYears(), job.getMinExperienceYears())
                    || !Objects.equals(seniority.maxYears(), job.getMaxExperienceYears());
            if (changed) {
                job.setRequiredSkills(skills);
                jobLevels.apply(job);
                job.setUpdatedAt(LocalDateTime.now());
                repository.save(job);
                reread++;
            }
        }
        if (reread > 0) {
            log.info("[JobSync] Re-read the skills and seniority of {} imported jobs with job-skills.yml", reread);
        }
        closeExpired();
    }

    /** Closes open imported postings older than their feed's max-age-days; returns how many. */
    public int closeExpired() {
        LocalDateTime now = LocalDateTime.now();
        int closed = 0;
        for (JobAnalysis job : repository.findAll()) {
            if (!"OPEN".equalsIgnoreCase(job.getStatus())) {
                continue;
            }
            Integer maxAge = jobFeeds.all().stream()
                    .filter(feed -> !feed.isPostedInApp() && JobFeeds.includes(feed, job))
                    .map(Feed::getMaxAgeDays)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            LocalDateTime posted = job.getPostedAt() != null ? job.getPostedAt() : job.getCreatedAt();
            if (maxAge != null && posted != null && posted.isBefore(now.minusDays(maxAge))) {
                job.setStatus("CLOSED");
                job.setUpdatedAt(now);
                repository.save(job);
                closed++;
            }
        }
        if (closed > 0) {
            log.info("[JobSync] Closed {} imported postings older than their feed's max-age-days", closed);
        }
        return closed;
    }

    /**
     * The list's skills the title or description names, then skills the source supplied that the list does not
     * have. List skills the text does not name as whole words (an older, looser match found them) are dropped.
     */
    List<String> refreshed(List<String> stored, String title, String description) {
        Map<String, String> skills = new LinkedHashMap<>();
        jobSkills.find(Objects.toString(title, "") + "\n" + Objects.toString(description, ""))
                .forEach(s -> skills.putIfAbsent(s.toLowerCase(Locale.ROOT), s));
        if (stored != null) {
            stored.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && !jobSkills.contains(s))
                    .forEach(s -> skills.putIfAbsent(s.toLowerCase(Locale.ROOT), s));
        }
        return new ArrayList<>(skills.values());
    }
}
