package org.vinod.shared.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EventContractSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void resumeParseEvent_serializesWithContractFieldNames() throws Exception {
        ResumeParseEvent event = ResumeParseEvent.builder()
                .resumeId("res-1")
                .userId(23L)
                .fileName("resume.pdf")
                .skills(List.of("Java"))
                .experienceYears(4.5)
                .location("Remote")
                .parsedAt(System.currentTimeMillis())
                .status("SUCCESS")
                .errorMessage(null)
                .build();

        JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(event));

        assertTrue(payload.has("resume_id"));
        assertTrue(payload.has("user_id"));
        assertTrue(payload.has("status"));
        assertTrue(payload.has("parsed_at"));
    }

    @Test
    void candidateMatchedEvent_serializesWithContractFieldNames() throws Exception {
        CandidateMatchedEvent event = CandidateMatchedEvent.builder()
                .matchId("m-1")
                .candidateId(23L)
                .jobId("JOB-9")
                .matchScore(91.0)
                .skillMatchPercentage(90.0)
                .experienceMatchPercentage(92.0)
                .matchedAt(System.currentTimeMillis())
                .status("NEW")
                .build();

        JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(event));

        assertTrue(payload.has("match_id"));
        assertTrue(payload.has("candidate_id"));
        assertTrue(payload.has("job_id"));
        assertTrue(payload.has("match_score"));
        assertTrue(payload.has("matched_at"));
    }

    @Test
    void asyncApiSpec_containsCanonicalChannelsForPublishedEvents() throws Exception {
        Path asyncApiPath = Path.of("..", "..", "contracts", "events", "asyncapi.yaml").normalize();
        String asyncApi = Files.readString(asyncApiPath);

        assertTrue(asyncApi.contains("resume.parsed:"));
        assertTrue(asyncApi.contains("matcher.candidate.matched:"));
        assertTrue(asyncApi.contains("name: ResumeParseEvent"));
        assertTrue(asyncApi.contains("name: CandidateMatchedEvent"));
    }
}

