package org.vinod.sha.matcher.service;

import org.junit.jupiter.api.Test;
import org.vinod.sha.matcher.entity.SkillListConverter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MatchSkillsAndStatusTest {

    @Test
    void splitsRequiredSkillsIntoMatchedAndMissing() {
        List<String> required = List.of("Java", "Spring", "Kubernetes", "CI/CD");
        List<String> candidate = List.of("java", " SPRING ", "Docker");

        assertEquals(List.of("Java", "Spring"), CandidateMatcherService.skillsIn(required, candidate, true));
        assertEquals(List.of("Kubernetes", "CI/CD"), CandidateMatcherService.skillsIn(required, candidate, false));
    }

    @Test
    void noCandidateSkillsMeansEverythingIsMissing() {
        assertEquals(List.of("Java"), CandidateMatcherService.skillsIn(List.of("Java"), null, false));
        assertEquals(List.of(), CandidateMatcherService.skillsIn(null, List.of("Java"), true));
    }

    @Test
    void skillListsRoundTripThroughOneColumn() {
        SkillListConverter converter = new SkillListConverter();
        List<String> skills = List.of("C++", "CI/CD", "Spring, Boot");

        assertEquals(skills, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(skills)));
        assertNull(converter.convertToDatabaseColumn(List.of()));
        assertEquals(List.of(), converter.convertToEntityAttribute(null));
    }
}
