package org.vinod.sha.analyzer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.vinod.sha.analyzer.dto.JobAnalyzeRequest;
import org.vinod.sha.analyzer.entity.JobAnalysis;
import org.vinod.sha.analyzer.repository.JobAnalysisRepository;
import org.vinod.sha.analyzer.service.JobAnalyzerService;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobAnalyzerIntegrationTest {

    @Mock
    private JobAnalysisRepository repository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    @Mock
    private Timer timer;

    @Mock
    private DistributionSummary salaryConfidenceSummary;

    @InjectMocks
    private JobAnalyzerService service;

    @BeforeEach
    void setUp() {
        when(meterRegistry.counter(anyString())).thenReturn(counter);
        when(meterRegistry.timer(anyString())).thenReturn(timer);

        ReflectionTestUtils.setField(service, "aiBaseUrl", "http://localhost:8008/api/ai");
        ReflectionTestUtils.setField(service, "salaryConfidenceSummary", salaryConfidenceSummary);
    }

    @Test
    void analyzeJob_happyPath_returnsAnalyzedDocument() {
        when(restTemplate.postForObject(any(String.class), any(), any(Class.class)))
                .thenReturn(Map.of("response", "Java, Spring Boot, Docker"));
        when(repository.save(any(JobAnalysis.class))).thenAnswer(invocation -> {
            JobAnalysis in = invocation.getArgument(0);
            in.setId("analysis-1");
            return in;
        });

        JobAnalysis result = service.analyze(JobAnalyzeRequest.builder()
                .jobId("job-101")
                .jobTitle("Senior Java Engineer")
                .companyName("Acme")
                .location("New York")
                .employmentType("FULL_TIME")
                .jobDescription("Need 5 years experience with Java, Spring Boot, Docker and AWS")
                .build());

        assertEquals("analysis-1", result.getId());
        assertEquals("ANALYZED", result.getStatus());
        assertNotNull(result.getRequiredSkills());
        assertTrue(result.getSalaryMin() > 0);
        assertTrue(result.getSalaryMax() > result.getSalaryMin());
    }

    @Test
    void getAnalysis_failPath_notFound_throws() {
        when(repository.findById("missing-id")).thenReturn(Optional.empty());
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.getById("missing-id"));
        assertTrue(ex.getMessage().contains("Analysis not found"));
    }
}

