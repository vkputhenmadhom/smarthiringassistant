package org.vinod.sha.analyzer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
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
 * At startup, re-reads imported jobs' skills with the current job-skills.yml, so a skill added to the list
 * (or a stricter match) reaches jobs imported before. Saving a job tells candidate-matcher. Jobs whose skills
 * do not change are not saved, so a restart with the same list touches nothing.
 */
@Slf4j
@Component
class JobSkillRefresh implements ApplicationRunner {

    private final JobAnalysisRepository repository;
    private final JobSkills jobSkills;

    JobSkillRefresh(JobAnalysisRepository repository, JobSkills jobSkills) {
        this.repository = repository;
        this.jobSkills = jobSkills;
    }

    @Override
    public void run(ApplicationArguments args) {
        int updated = 0;
        for (JobAnalysis job : repository.findAll()) {
            if (job.getSource() == null || job.getSource().isBlank()) {
                continue; // posted in the app: HR chose its skills
            }
            List<String> skills = refreshed(job.getRequiredSkills(), job.getJobTitle(), job.getJobDescription());
            if (!sameSkills(skills, job.getRequiredSkills())) {
                job.setRequiredSkills(skills);
                job.setUpdatedAt(LocalDateTime.now());
                repository.save(job);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("[JobSync] Re-read the skills of {} imported jobs with job-skills.yml", updated);
        }
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

    private static boolean sameSkills(List<String> a, List<String> b) {
        return Objects.equals(a, b == null ? List.of() : b);
    }
}
