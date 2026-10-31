package org.vinod.sha.analyzer.service;

import org.junit.jupiter.api.Test;
import org.vinod.sha.analyzer.service.JobLevels.Seniority;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Against the shipped job-skills.yml levels */
class JobLevelsTest {

    private final JobLevels levels = ConfiguredFeeds.levels();

    @Test
    void titleLevelGivesTheRange() {
        assertEquals(new Seniority("Lead", 10, null), levels.of("Principal Engineer", null));
        assertEquals(new Seniority("Lead", 10, null), levels.of("Associate Lead Software Engineer", null));
        assertEquals(new Seniority("Lead", 10, null), levels.of("Technical Program Manager", null));
        assertEquals(new Seniority("Senior", 5, 12), levels.of("Sr Software Engineer", null));
        assertEquals(new Seniority("Entry", 0, 2), levels.of("Software Engineer, New Grad", null));
        assertEquals(Seniority.UNKNOWN, levels.of("Java Developer", "Build services in Java"));
    }

    @Test
    void statedYearsWin() {
        assertEquals(new Seniority(null, 5, null), levels.of("Data Engineer || Agentic AI, Python, AWS || 5+ Yrs", null));
        assertEquals(new Seniority("Senior", 8, 12), levels.of("Senior Java Developer", "We need 8-12 years of hands-on experience."));
        assertEquals(new Seniority(null, 3, 5), levels.of("Java Developer (3 to 5 yrs)", null));
        assertEquals(new Seniority("Senior", 7, 12), levels.of("Senior Engineer", "7+ years of relevant experience in Java"));
    }

    @Test
    void yearsThatAreNotAboutExperienceAreIgnored() {
        assertEquals(Seniority.UNKNOWN, levels.of("Java Developer", "A company with 25 years of history. Founded 2001."));
    }
}
