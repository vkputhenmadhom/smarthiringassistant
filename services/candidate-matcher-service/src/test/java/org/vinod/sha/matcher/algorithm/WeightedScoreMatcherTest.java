package org.vinod.sha.matcher.algorithm;

import org.junit.jupiter.api.Test;
import org.vinod.sha.matcher.config.MatchingRules;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class WeightedScoreMatcherTest {

    private final WeightedScoreMatcher matcher = new WeightedScoreMatcher(new MatchingRules(3, 40,
            Map.of("reactjs", "react", "golang", "go"),
            Map.of("bangalore", "bengaluru"),
            new MatchingRules.Seniority(8, List.of(
                    new MatchingRules.Level("entry", List.of("new grad", "junior"), 0, 2),
                    new MatchingRules.Level("lead", List.of("principal", "lead", "staff"), 10, 40),
                    new MatchingRules.Level("senior", List.of("senior", "sr"), 5, 14),
                    new MatchingRules.Level("mid", List.of("ii"), 2, 6)))));

    private WeightedScoreMatcher.Score score(List<String> candidate, List<String> required, String title, String description,
                                             Double years, Integer requiredYears, String location, List<String> preferred,
                                             String jobLocation) {
        return matcher.score(candidate, required, title, description, years, requiredYears, location, preferred, jobLocation);
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
    void titleLevelStandsInForUnstatedYears() {
        assertEquals(100.0, matcher.seniorityScore(18.7, "Principal Engineer"));
        assertEquals(100.0, matcher.seniorityScore(18.7, "Associate Lead Software Engineer"), "lead comes before mid");
        assertEquals(100.0 - 4.7 * 8, matcher.seniorityScore(18.7, "Sr Software Engineer"), 1e-9);
        assertEquals(20.0, matcher.seniorityScore(18.7, "Software Engineer, New Grad"));
        assertEquals(50.0, matcher.seniorityScore(1.0, "Software Engineer II"));
        assertNull(matcher.seniorityScore(18.7, "Java Developer"), "no level named");
        assertNull(matcher.seniorityScore(null, "Senior Engineer"));

        var score = score(List.of("Java", "SQL", "Spring"), List.of("Java", "SQL", "Spring"), "Software Engineer, New Grad",
                null, 18.7, 0, null, List.of(), null);
        assertEquals(20.0, score.experience());
    }

    @Test
    void experienceGivesPartialCreditAndZeroYearsMeansUnknown() {
        assertEquals(50.0, WeightedScoreMatcher.experienceScore(2.0, 4));
        assertNull(WeightedScoreMatcher.experienceScore(2.0, 0));
        assertNull(WeightedScoreMatcher.experienceScore(null, 4));
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
