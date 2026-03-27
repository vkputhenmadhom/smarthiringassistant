package org.vinod.sha.screening;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.vinod.sha.screening.dto.CreateSessionRequest;
import org.vinod.sha.screening.dto.SubmitStageResponseRequest;
import org.vinod.sha.screening.entity.ScreeningSession;
import org.vinod.sha.screening.repository.ScreeningSessionRepository;
import org.vinod.sha.screening.repository.WorkflowSagaStateRepository;
import org.vinod.sha.screening.service.ScreeningBotService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningBotIntegrationTest {

    @Mock
    private ScreeningSessionRepository repository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    @Mock
    private DistributionSummary finalScoreSummary;

    @Mock
    private WorkflowSagaStateRepository sagaStateRepository;

    @InjectMocks
    private ScreeningBotService service;

    @BeforeEach
    void setUp() {
        when(meterRegistry.counter(anyString())).thenReturn(counter);
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);

        ReflectionTestUtils.setField(service, "stages", List.of("initial", "technical", "behavioral"));
        ReflectionTestUtils.setField(service, "finalScoreSummary", finalScoreSummary);
    }

    @Test
    void screeningPipeline_happyPath_completesWithPass() {
        when(repository.save(any(ScreeningSession.class))).thenAnswer(invocation -> {
            ScreeningSession s = invocation.getArgument(0);
            if (s.getId() == null) {
                s.setId("session-1");
            }
            if (s.getFailureReasons() == null) {
                s.setFailureReasons(new ArrayList<>());
            }
            return s;
        });
        when(repository.findById("session-1")).thenAnswer(invocation -> Optional.of(state));

        state = service.createSession(CreateSessionRequest.builder().candidateId(99L).jobId("job-101").build());
        state = service.submitResponse("session-1", SubmitStageResponseRequest.builder()
                .stage("initial")
                .response("Example: I led a reliability initiative across multiple services, defined SLOs, added golden signal dashboards, and drove incident reviews. " +
                        "The result was a measurable impact on customer experience: 40% lower latency, 55% fewer Sev2 incidents, improved availability to 99.95%, " +
                        "and faster release confidence with canary analysis and clear rollback playbooks.")
                .build());
        state = service.advance("session-1");

        assertNotNull(state.getStageResults());
        assertEquals(1, state.getStageResults().size());
        assertNotEquals("FAIL", state.getDecision());
    }

    private ScreeningSession state;

    @Test
    void getSession_failPath_notFound_throws() {
        when(repository.findById("missing-session")).thenReturn(Optional.empty());
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.getSession("missing-session"));
        assertTrue(ex.getMessage().contains("not found"));
    }
}

