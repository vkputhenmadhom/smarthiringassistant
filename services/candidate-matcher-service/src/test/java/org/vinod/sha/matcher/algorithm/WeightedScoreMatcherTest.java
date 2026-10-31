package org.vinod.sha.matcher.algorithm;

import org.junit.jupiter.api.Test;
import org.vinod.sha.matcher.config.MatchingRules;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class WeightedScoreMatcherTest {

    private final WeightedScoreMatcher matcher = new WeightedScoreMatcher(new MatchingRules(3, 40, 8,
            Map.of("reactjs", "react", "golang", "go"),
            Map.of("bangalore", "bengaluru")));

    private WeightedScoreMatcher.Score score(List<String> candidate, List<String> required, String title, String description,
                                             Double years, Integer requiredYears, String location, List<String> preferred,
                                             String jobLocation) {
        return matcher.score(candidate, required, title, description, years, requiredYears, null, location, preferred, jobLocation);
    }

    @Test
    void jobThatStatesNothingRelevantIsNotAMatch() {
        var score = score(List.of("Java", "Spring"), List.of(), "Sales Executive",
                "Build trust with retail clients", 5.0, 0, "Bengaluru", List.of(), null);

        assertFalse(score.hasRelevantSkill());
        assertEquals(0.0, score.overall());
        assertNull(score.experience());
        assertNull(score.location());
    }

    @Test
    void oneListedSkillCannotGiveFullMarks() {
        // The Snowflake job listed only "Sql": it used to score 100% for anyone with SQL.
        var score = score(List.of("Java", "SQL", "React"), List.of("Sql"), "Custom Software Engineer (Snowflake)",
                "Build data pipelines in Snowflake", null, 0, null, List.of(), null);

        assertEquals(100.0 / 3, score.skills(), 1e-9);
        assertEquals(List.of("Sql"), score.matched());
    }

    @Test
    void skillsTheDescriptionNamesCountHalf() {
        var score = score(List.of("Java", "Spring Boot", "Kafka"), List.of("Java"), "Backend Engineer",
                "Java services with Spring Boot and Kafka", null, 0, null, List.of(), null);

        // 1 listed + 0.5 × 2 named = 2 of at least 3
        assertEquals(200.0 / 3, score.skills(), 1e-9);
        assertEquals(List.of("Java", "Spring Boot", "Kafka"), score.matched());
    }

    @Test
    void skillAliasesMatchOtherSpellings() {
        var score = score(List.of("ReactJS", "Golang"), List.of("React", "Go", "Docker"), "Engineer", null,
                null, 0, null, List.of(), null);

        assertEquals(200.0 / 3, score.skills(), 1e-9);
        assertEquals(List.of("React", "Go"), score.matched());
        assertEquals(List.of("Docker"), score.missing());
    }

    @Test
    void unknownPartsAreLeftOutInsteadOfScored() {
        var score = score(List.of("Java", "Spring", "SQL"), List.of("java", "spring", "sql"), "Backend Engineer",
                null, null, 0, "Pune", List.of(), null);

        assertEquals(100.0, score.overall());
        assertNull(score.experience());
        assertNull(score.location());
    }

    @Test
    void knownPartsAreWeighted() {
        // skills 50 * 0.4 + experience 100 * 0.3 + location 0 * 0.3 = 50
        var score = score(List.of("Java", "Kafka"), List.of("Java", "Kafka", "Go", "Rust"), "Engineer", null,
                6.0, 3, "Pune", List.of(), "Chennai, Tamil Nadu");

        assertEquals(50.0, score.skills());
        assertEquals(100.0, score.experience());
        assertEquals(0.0, score.location());
        assertEquals(50.0, score.overall(), 1e-9);
    }

    @Test
    void lackingASkillTheTitleNamesCapsTheSkillScore() {
        // The .NET job listed only side skills the candidate has; .NET itself is in the title.
        var score = score(List.of("Java", "SQL", "Angular"), List.of("SQL", "Angular", ".NET"),
                "Senior Software Engineer - .NET", null, null, 0, null, List.of(), null);

        assertEquals(40.0, score.skills());
        assertEquals(List.of(".NET"), score.missing());
    }

    @Test
    void overQualifiedCandidatesLosePointsPerYear() {
        assertEquals(100.0, matcher.experienceScore(18.7, 10, null), "Lead: 10+ years");
        assertEquals(100.0 - 6.7 * 8, matcher.experienceScore(18.7, 5, 12), 1e-9, "Senior: 5-12");
        assertEquals(20.0, matcher.experienceScore(18.7, 0, 2), "Entry: 0-2");
        assertEquals(50.0, matcher.experienceScore(1.0, 2, 6));
        assertNull(matcher.experienceScore(18.7, 0, null), "nothing known about the job");
        assertNull(matcher.experienceScore(null, 5, 12));

        var score = matcher.score(List.of("Java", "SQL", "Spring"), List.of("Java", "SQL", "Spring"), "Software Engineer, New Grad",
                null, 18.7, 0, 2, null, List.of(), null);
        assertEquals(20.0, score.experience());
    }

    @Test
    void experienceGivesPartialCreditAndZeroYearsMeansUnknown() {
        assertEquals(50.0, matcher.experienceScore(2.0, 4, null));
        assertNull(matcher.experienceScore(2.0, 0, null));
        assertNull(matcher.experienceScore(null, 4, null));
    }

    @Test
    void locationUsesTheBestOfWhereTheyLiveAndWhereTheyWouldWork() {
        assertEquals(100.0, matcher.locationScore("Kochi, Kerala", List.of("Bangalore", "Remote"), "Bengaluru, Karnataka"),
                "same city, through the alias");
        assertEquals(100.0, matcher.locationScore("Kochi", List.of("Remote"), "Remote"));
        assertEquals(50.0, matcher.locationScore("Kerala", List.of(), "Kochi, Kerala"), "same state");
        assertEquals(75.0, matcher.locationScore("Pune", List.of(), "Remote"), "a remote job can be done from Pune");
        assertEquals(0.0, matcher.locationScore("Austin, TX", List.of("Remote"), "Gurugram, Haryana"),
                "open to remote is no fit for an onsite job elsewhere");
        assertEquals(0.0, matcher.locationScore("Pune", List.of("Chennai"), "Delhi"));
        assertNull(matcher.locationScore(null, List.of(), "Pune"));
        assertNull(matcher.locationScore("Pune", List.of(), " "));
    }
}
