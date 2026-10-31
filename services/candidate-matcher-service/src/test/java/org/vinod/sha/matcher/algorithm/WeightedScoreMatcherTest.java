package org.vinod.sha.matcher.algorithm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class WeightedScoreMatcherTest {

    private final WeightedScoreMatcher matcher = new WeightedScoreMatcher();

    @Test
    void jobThatStatesNothingRelevantIsNotAMatch() {
        // The old scorer gave 30 free experience points here, which was enough to match.
        var score = matcher.score(List.of("Java", "Spring"), List.of(), "Sales Executive",
                "Build trust with retail clients", 5.0, 0, "Bengaluru", null);

        assertFalse(score.hasRelevantSkill());
        assertEquals(0.0, score.overall());
        assertNull(score.experience());
        assertNull(score.location());
    }

    @Test
    void unknownPartsAreLeftOutInsteadOfScored() {
        var score = matcher.score(List.of("Java", "Spring"), List.of("java", "spring"), "Backend Engineer",
                null, null, 0, "Pune", null);

        assertEquals(100.0, score.overall());
        assertNull(score.experience());
        assertNull(score.location());
    }

    @Test
    void knownPartsAreWeighted() {
        // skills 50 * 0.4 + experience 100 * 0.3 + location 0 * 0.3 = 50
        var score = matcher.score(List.of("Java"), List.of("Java", "Kafka"), "Engineer", null,
                6.0, 3, "Pune", "Chennai, Tamil Nadu");

        assertEquals(50.0, score.skills());
        assertEquals(100.0, score.experience());
        assertEquals(0.0, score.location());
        assertEquals(50.0, score.overall(), 1e-9);
    }

    @Test
    void jobWithoutSkillListIsJudgedByWholeWordMentions() {
        assertEquals(100.0 / 3, WeightedScoreMatcher.skillScore(List.of("Java", "Rust"), List.of(),
                "Java Developer", "Earn our clients' trust"), 1e-9);
        assertEquals(0.0, WeightedScoreMatcher.skillScore(List.of("Java"), null,
                "JavaScript Developer", null));
        assertEquals(100.0, WeightedScoreMatcher.skillScore(List.of("Java", "Spring", "SQL", "Docker"), List.of(),
                "Java Developer", "Spring, SQL and Docker"), 1e-9);
    }

    @Test
    void experienceGivesPartialCreditAndZeroYearsMeansUnknown() {
        assertEquals(50.0, WeightedScoreMatcher.experienceScore(2.0, 4));
        assertNull(WeightedScoreMatcher.experienceScore(2.0, 0));
        assertNull(WeightedScoreMatcher.experienceScore(null, 4));
    }

    @Test
    void locationScores() {
        assertEquals(100.0, WeightedScoreMatcher.locationScore("pune", "Pune"));
        assertEquals(50.0, WeightedScoreMatcher.locationScore("Pune", "Pune, Maharashtra"));
        assertEquals(75.0, WeightedScoreMatcher.locationScore("Pune", "Remote"));
        assertEquals(0.0, WeightedScoreMatcher.locationScore("Pune", "Chennai"));
        assertNull(WeightedScoreMatcher.locationScore("Pune", " "));
        assertNull(WeightedScoreMatcher.locationScore(null, "Pune"));
    }
}
