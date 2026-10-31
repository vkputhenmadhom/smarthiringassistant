package org.vinod.sha.analyzer;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.vinod.sha.analyzer.dto.JobAnalyzeRequest;
import org.vinod.sha.analyzer.entity.JobAnalysis;
import org.vinod.sha.analyzer.repository.JobAnalysisRepository;
import org.vinod.sha.analyzer.service.JobAnalyzerService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobAnalyzerIntegrationTest {

    @Mock
    private JobAnalysisRepository repository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RestTemplate restTemplate;

    private MeterRegistry meterRegistry;

    @Mock
    private DistributionSummary salaryConfidenceSummary;

    private JobAnalyzerService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new JobAnalyzerService(repository, rabbitTemplate, restTemplate, meterRegistry,
                new org.vinod.sha.analyzer.service.JobMarket(""),
                org.vinod.sha.analyzer.service.ConfiguredFeeds.load());

        ReflectionTestUtils.setField(service, "aiBaseUrl", "http://localhost:8008/api/ai");
        ReflectionTestUtils.setField(service, "salaryConfidenceSummary", salaryConfidenceSummary);
    }

    @Test
    void analyzeJob_happyPath_returnsAnalyzedDocument() {
        when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
                .thenReturn(Map.of("response", "Java, Spring Boot, Docker"));
        when(repository.save(any(JobAnalysis.class))).thenAnswer(invocation -> {
            JobAnalysis in = invocation.getArgument(0);
            in.setId("analysis-1");
            return in;
        });

        JobAnalysis result = service.analyze(new JobAnalyzeRequest(
                "job-101",
                "Senior Java Engineer",
                "Acme",
                "New York",
                "FULL_TIME",
                "Need 5 years experience with Java, Spring Boot, Docker and AWS"
        ));

        assertEquals("analysis-1", result.getId());
        assertEquals("ANALYZED", result.getStatus());
        assertNotNull(result.getRequiredSkills());
        assertTrue(result.getSalaryMin() > 0);
        assertTrue(result.getSalaryMax() > result.getSalaryMin());
    }

    @Test
    void candidatesSeeOnlyTheJobMarketWhileHrSeesEverything() {
        JobAnalyzerService india = new JobAnalyzerService(repository, rabbitTemplate, restTemplate, meterRegistry,
                new org.vinod.sha.analyzer.service.JobMarket("IN"),
                org.vinod.sha.analyzer.service.ConfiguredFeeds.load());
        when(repository.findAll()).thenReturn(List.of(
                JobAnalysis.builder().id("in").source("JSEARCH_TECH_IN").status("OPEN").createdAt(java.time.LocalDateTime.now()).build(),
                JobAnalysis.builder().id("us").source("JSEARCH").status("OPEN").createdAt(java.time.LocalDateTime.now()).build(),
                JobAnalysis.builder().id("hr").status("OPEN").createdAt(java.time.LocalDateTime.now()).build()));

        assertEquals(2, india.listJobs(0, 20, null, null, null, true).get("totalElements"));
        assertEquals(3, india.listJobs(0, 20, null, null, null, false).get("totalElements"));
        assertEquals(2, india.getJobDashboardMetrics(true).get("openJobs"));
    }

    @Test
    void eachPortalTabListsItsOwnFeed() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        when(repository.findAll()).thenReturn(List.of(
                JobAnalysis.builder().id("tech-a").source("ADZUNA_TECH_IN").status("OPEN").createdAt(now).build(),
                JobAnalysis.builder().id("tech-j").source("JSEARCH_TECH_IN").status("OPEN").createdAt(now).build(),
                JobAnalysis.builder().id("govt").source("ADZUNA_GOVT_IN").status("OPEN").createdAt(now).build(),
                JobAnalysis.builder().id("remote").source("JSEARCH_REMOTE_IN").status("OPEN").createdAt(now).build(),
                JobAnalysis.builder().id("posted").status("OPEN").createdAt(now).build(),
                JobAnalysis.builder().id("us").source("JSEARCH").status("OPEN").createdAt(now).build()));

        assertEquals(List.of("tech-a", "tech-j"), ids(service.listJobs(0, 20, null, null, "tech", false)));
        assertEquals(List.of("govt"), ids(service.listJobs(0, 20, null, null, "govt", false)));
        assertEquals(List.of("remote"), ids(service.listJobs(0, 20, null, null, "REMOTE", false)));
        assertEquals(List.of("posted"), ids(service.listJobs(0, 20, null, null, "posted", false)));
        assertEquals(List.of("us"), ids(service.listJobs(0, 20, null, null, "JSEARCH", false)), "an exact source tag");
    }

    @SuppressWarnings("unchecked")
    private static List<String> ids(java.util.Map<String, Object> page) {
        return ((List<JobAnalysis>) page.get("content")).stream().map(JobAnalysis::getId).sorted().toList();
    }

    @Test
    void getAnalysis_failPath_notFound_throws() {
        when(repository.findById("missing-id")).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getById("missing-id"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode(), "the gateway reports a missing job as NOT_FOUND, not as an outage");
        assertTrue(ex.getMessage().contains("Analysis not found"));
    }
}

