package org.vinod.sha.matcher.service;

import org.junit.jupiter.api.Test;
import org.vinod.sha.matcher.entity.SkillListConverter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MatchSkillsAndStatusTest {

    @Test
    void skillListsRoundTripThroughOneColumn() {
        SkillListConverter converter = new SkillListConverter();
        List<String> skills = List.of("C++", "CI/CD", "Spring, Boot");

        assertEquals(skills, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(skills)));
        assertNull(converter.convertToDatabaseColumn(List.of()));
        assertEquals(List.of(), converter.convertToEntityAttribute(null));
    }
}
