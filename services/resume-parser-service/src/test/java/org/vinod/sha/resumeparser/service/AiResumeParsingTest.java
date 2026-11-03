package org.vinod.sha.resumeparser.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.vinod.sha.resumeparser.entity.ParsedResumeData;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiResumeParsingTest {

    private final AiResumeAnalyzer analyzer = new AiResumeAnalyzer(false, "http://unused", 12000, 5, new ObjectMapper());

    @Test
    void readsTheJsonEvenInsideCodeFencesWithUnknownKeys() {
        String answer = """
                ```json
                {"fullName":"Asha Menon","currentLocation":"Kochi, Kerala, India","preferredLocations":["Bengaluru","Remote"],
                 "totalExperienceYears":12.5,"skills":["Java","Spring Boot"],"seniority":"senior",
                 "experience":[{"company":"Acme","title":"Lead Engineer","startDate":"2020-01","endDate":"Present"}],
                 "education":[{"institution":"CUSAT","degree":"B.Tech","field":"CS","graduationYear":2008}]}
                ```""";

        AiResumeAnalyzer.AiResume ai = analyzer.parse(answer).orElseThrow();

        assertEquals("Asha Menon", ai.fullName());
        assertEquals(List.of("Bengaluru", "Remote"), ai.preferredLocations());
        assertEquals(12.5, ai.totalExperienceYears());
        assertEquals("Lead Engineer", ai.experience().get(0).title());
    }

    @Test
    void unreadableAnswerIsEmpty() {
        assertTrue(analyzer.parse("Sorry, I cannot help with that.").isEmpty());
        assertTrue(analyzer.parse("{not json}").isEmpty());
        assertTrue(analyzer.parse(null).isEmpty());
    }

    @Test
    void switchedOffMeansNoCall() {
        assertTrue(analyzer.analyze(32L, "Java developer").isEmpty());
    }

    @Test
    void aiWinsWhereItFoundSomethingAndTheRulesFillTheRest() {
        ParsedResumeData data = ParsedResumeData.builder()
                .fullName("").email("asha@example.com").phone("+91 98765 43210").location("US")
                .skills(new ArrayList<>(List.of("Java", "Docker"))).totalExperienceYears(0.0)
                .build();
        AiResumeAnalyzer.AiResume ai = new AiResumeAnalyzer.AiResume("Asha Menon", "", "", "Kochi, Kerala, India",
                List.of("Bengaluru", " Remote ", "Bengaluru"), 12.5, List.of("Java", "Spring Boot", "Kafka"),
                List.of("Lead Engineer"), "Backend lead.", List.of(), List.of());

        ResumeParserService.applyAi(data, ai);

        assertEquals("Asha Menon", data.getFullName());
        assertEquals("asha@example.com", data.getEmail(), "AI found none; the pattern's stays");
        assertEquals("Kochi, Kerala, India", data.getLocation(), "not the US guessed from 'Relocated from US'");
        assertEquals("Bengaluru; Remote", data.getPreferredLocations());
        assertEquals(12.5, data.getTotalExperienceYears());
        assertEquals(List.of("Java", "Spring Boot", "Kafka", "Docker"), data.getSkills());
    }
}
