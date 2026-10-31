package org.vinod.sha.analyzer.service;

import org.junit.jupiter.api.Test;
import org.vinod.sha.analyzer.entity.JobAnalysis;
import org.vinod.sha.analyzer.repository.JobAnalysisRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobDetailsRefreshTest {

    private final JobAnalysisRepository repository = mock(JobAnalysisRepository.class);
    private final JobDetailsRefresh refresh = new JobDetailsRefresh(repository, ConfiguredFeeds.skills(), ConfiguredFeeds.levels(), ConfiguredFeeds.load());

    @Test
    void dotNetJobGetsItsCoreSkills() {
        // Imported with the old 30-term list, which had no C# or .NET
        // In job-skills.yml order
        assertEquals(List.of("C#", "SQL", ".NET", "ASP.NET", "ASP.NET Core", "Azure"),
                refresh.refreshed(List.of("Azure", "Sql"), "Senior Software Engineer - C#, .Net & ASP.Net Core",
                        "Build services on Azure with SQL"));
    }

    @Test
    void keepsSourceSkillsAndDropsOldLooseMatches() {
        // "Rust" came from "trust"; "Stakeholder management" came from JSearch's own list
        assertEquals(List.of("Java", "Stakeholder management"),
                refresh.refreshed(List.of("Rust", "Java", "Stakeholder management"), "Java Developer", "Earn client trust"));
    }

    @Test
    void savesOnlyImportedJobsWhoseSkillsChange() {
        JobAnalysis changed = JobAnalysis.builder().id("a").source("JSEARCH_TECH_IN").jobTitle("Senior .NET Engineer")
                .requiredSkills(List.of("Sql")).jobDescription("SQL").build();
        JobAnalysis same = JobAnalysis.builder().id("b").source("ADZUNA_TECH_IN").jobTitle("Java Developer")
                .requiredSkills(List.of("Java")).build();
        JobAnalysis posted = JobAnalysis.builder().id("c").jobTitle("Rust Engineer").requiredSkills(List.of("Go")).build();
        when(repository.findAll()).thenReturn(List.of(changed, same, posted));

        refresh.run(null);

        verify(repository, times(1)).save(changed);
        verify(repository, never()).save(same);
        verify(repository, never()).save(posted);
        assertEquals(List.of("SQL", ".NET"), changed.getRequiredSkills());
        assertEquals("Senior", changed.getSeniority());
        assertEquals(5, changed.getMinExperienceYears());
    }

    @Test
    void closesImportsOlderThanTheirFeedsMaxAge() {
        JobAnalysis old = JobAnalysis.builder().id("old").source("ADZUNA_TECH_IN").status("OPEN")
                .postedAt(java.time.LocalDateTime.now().minusDays(45)).build();
        JobAnalysis fresh = JobAnalysis.builder().id("new").source("ADZUNA_TECH_IN").status("OPEN")
                .postedAt(java.time.LocalDateTime.now().minusDays(3)).build();
        JobAnalysis posted = JobAnalysis.builder().id("hr").status("OPEN")
                .postedAt(java.time.LocalDateTime.now().minusDays(400)).build();
        when(repository.findAll()).thenReturn(List.of(old, fresh, posted));

        assertEquals(1, refresh.closeExpired());
        assertEquals("CLOSED", old.getStatus());
        assertEquals("OPEN", fresh.getStatus());
        assertEquals("OPEN", posted.getStatus(), "jobs posted in the app are HR's to close");
    }
}
