package org.vinod.sha.gateway.graphql;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DashboardActivityTest {

    @Test
    void saysWhoDidWhatWithNamesAndJobTitles() {
        Map<String, Object> asha = Map.of("userId", 32, "name", "Asha Menon", "createdAt", "2026-10-31T04:15:00");
        Map<String, Object> applied = Map.of("candidateId", 32, "jobId", "j1", "status", "IN_PROGRESS", "createdAt", "2026-10-31T05:00:00");
        Map<String, Object> done = new HashMap<>(Map.of("candidateId", 7, "jobId", "j2", "status", "COMPLETED",
                "decision", "PASS", "createdAt", "2026-10-31T06:00:00", "completedAt", "2026-10-31T07:00:00"));
        Map<String, Object> posted = Map.of("type", "NEW_JOB", "description", "Job posted: QA Lead (open)", "timestamp", "2026-10-30T09:00:00");

        List<Map<String, Object>> activity = DashboardActivity.build(List.of(asha), List.of(applied, done),
                Map.of("j1", "Software Engineer, Accenture"), List.of(posted));

        assertEquals(List.of(
                "Job posted: QA Lead (open)",
                "Asha Menon uploaded a resume",
                "Asha Menon applied to Software Engineer, Accenture",
                "Candidate #7 applied to a job",
                "Candidate #7 completed screening for a job (pass)"),
                activity.stream().map(a -> a.get("description")).toList());
        assertEquals("2026-10-31T07:00:00", activity.get(4).get("timestamp"));
    }

    @Test
    void candidateWithoutAParsedNameIsNumbered() {
        Map<String, Object> noName = new HashMap<>();
        noName.put("userId", 9);
        noName.put("name", null);
        assertEquals("Candidate #9 uploaded a resume",
                DashboardActivity.build(List.of(noName), List.of(), Map.of(), List.of()).get(0).get("description"));
    }
}
