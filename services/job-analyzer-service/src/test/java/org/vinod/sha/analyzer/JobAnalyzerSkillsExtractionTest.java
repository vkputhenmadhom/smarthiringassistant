package org.vinod.sha.analyzer;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.vinod.sha.analyzer.dto.SkillsExtractResponse;
import org.vinod.sha.analyzer.repository.JobAnalysisRepository;
import org.vinod.sha.analyzer.service.JobAnalyzerService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobAnalyzerSkillsExtractionTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private JobAnalyzerService service;

    @BeforeEach
    void setUp() {
        service = new JobAnalyzerService(mock(JobAnalysisRepository.class), mock(RabbitTemplate.class),
                restTemplate, new SimpleMeterRegistry(), new org.vinod.sha.analyzer.service.JobMarket(""),
                org.vinod.sha.analyzer.service.ConfiguredFeeds.load());
        ReflectionTestUtils.setField(service, "aiBaseUrl", "http://ai-integration-service:8008/api/ai");
    }

    @Test
    void callsTheAiServiceOncePerExtraction() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of("response", "Elixir, Phoenix LiveView"));

        service.extractSkillsFromText("Backend engineer with Elixir and Phoenix LiveView");

        verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    void mergesAiSkillsWithDictionaryMatches() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of("response", "Elixir, Phoenix LiveView"));

        SkillsExtractResponse skills = service.extractSkillsFromText("Elixir and Phoenix LiveView with Docker");

        String required = String.join(",", skills.requiredSkills()).toLowerCase();
        assertTrue(required.contains("elixir") && required.contains("docker"), required);
    }

    @Test
    void fallsBackToTheDictionaryWhenTheAiServiceFails() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new RestClientException("ai-integration-service unavailable"));

        SkillsExtractResponse skills = service.extractSkillsFromText("Java and Kafka microservices");

        String required = String.join(",", skills.requiredSkills()).toLowerCase();
        assertTrue(required.contains("java") && required.contains("kafka"), required);
        verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(Map.class));
    }
}
