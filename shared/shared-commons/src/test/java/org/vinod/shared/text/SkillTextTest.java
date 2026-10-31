package org.vinod.shared.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillTextTest {

    @Test
    void matchesWholeWordsOnly() {
        assertFalse(SkillText.mentions("Build trust with clients", "Rust"));
        assertFalse(SkillText.mentions("Strong JavaScript skills", "Java"));
        assertTrue(SkillText.mentions("Java, Spring Boot and SQL.", "java"));
        assertTrue(SkillText.mentions("Experience with node.js required", "Node.js"));
        assertTrue(SkillText.mentions("Owns the CI/CD pipeline", "CI/CD"));
    }

    @Test
    void plusAndHashContinueAWord() {
        assertTrue(SkillText.mentions("Modern C++ developer", "C++"));
        assertFalse(SkillText.mentions("Modern C++ developer", "C"));
        assertFalse(SkillText.mentions("C# and .NET", "C"));
    }

    @Test
    void everydayWordsNeedTheirUsualCapitals() {
        assertFalse(SkillText.mentions("A good fit who can go the extra mile", "Go"));
        assertTrue(SkillText.mentions("Backend services in Go and Rust", "go"));
        assertFalse(SkillText.mentions("r&d team", "R"));
    }

    @Test
    void findKeepsDictionaryOrder() {
        assertEquals(List.of("Python", "Docker"),
                SkillText.find("docker images for python apps; trust and safety", List.of("Python", "Rust", "Docker")));
        assertEquals(List.of(), SkillText.find(null, List.of("Java")));
    }
}
