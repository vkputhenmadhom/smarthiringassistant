package org.vinod.sha.resumeparser.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads a resume with ai-integration-service's /analyze-resume (instructions in its ai-prompts.yml). Any
 * failure (switched off, no API key, quota, timeout, unreadable answer) yields empty, and the rule-based
 * parser's result stands.
 */
@Slf4j
@Component
public class AiResumeAnalyzer {

    /** The JSON object /analyze-resume answers with; unknown keys are ignored. */
    public record AiResume(String fullName, String email, String phone, String currentLocation,
                           List<String> preferredLocations, Double totalExperienceYears, List<String> skills,
                           List<String> jobTitles, String summary, List<Job> experience, List<School> education) {

        public record Job(String company, String title, String startDate, String endDate, String description) {
        }

        public record School(String institution, String degree, String field, Integer graduationYear) {
        }
    }

    private final boolean enabled;
    private final int maxChars;
    private final RestClient client;
    private final ObjectReader reader;

    public AiResumeAnalyzer(@Value("${resume-parser.ai.enabled:true}") boolean enabled,
                            @Value("${resume-parser.ai.base-url:http://ai-integration-service:8008/api/ai}") String baseUrl,
                            @Value("${resume-parser.ai.max-chars:20000}") int maxChars,
                            @Value("${resume-parser.ai.timeout-seconds:90}") int timeoutSeconds,
                            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.maxChars = maxChars;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.reader = objectMapper.readerFor(AiResume.class).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public Optional<AiResume> analyze(Long userId, String resumeText) {
        if (!enabled || resumeText == null || resumeText.isBlank()) {
            return Optional.empty();
        }
        String text = resumeText.length() > maxChars ? resumeText.substring(0, maxChars) : resumeText;
        try {
            Map<?, ?> response = client.post().uri("/analyze-resume")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("userId", "resume-parser:" + userId, "prompt", text))
                    .retrieve()
                    .body(Map.class);
            Object answer = response == null ? null : response.get("response");
            Optional<AiResume> parsed = parse(answer == null ? null : answer.toString());
            if (parsed.isEmpty()) {
                log.warn("AI resume analysis for user {} gave no readable JSON; using the rule-based parser", userId);
            }
            return parsed;
        } catch (Exception e) {
            log.warn("AI resume analysis for user {} failed ({}); using the rule-based parser", userId, e.getMessage());
            return Optional.empty();
        }
    }

    /** The JSON object in the answer, allowing for code fences or text around it. */
    Optional<AiResume> parse(String answer) {
        if (answer == null) {
            return Optional.empty();
        }
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Optional.empty();
        }
        try {
            return Optional.of(reader.readValue(answer.substring(start, end + 1)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
