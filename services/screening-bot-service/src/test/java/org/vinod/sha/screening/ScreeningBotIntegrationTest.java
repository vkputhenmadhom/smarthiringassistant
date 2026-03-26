package org.vinod.sha.screening;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningBotIntegrationTest {

    @Mock
    private ScreeningSessionRepository repository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private WorkflowSagaStateRepository sagaStateRepository;

    private ScreeningBotService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new ScreeningBotService(repository, rabbitTemplate, new SimpleMeterRegistry(), sagaStateRepository);
        java.lang.reflect.Field f = ScreeningBotService.class.getDeclaredField("stages");
        f.setAccessible(true);
        f.set(service, List.of("initial", "technical", "behavioral"));
        java.lang.reflect.Method m = ScreeningBotService.class.getDeclaredMethod("initMetrics");
        m.setAccessible(true);
        m.invoke(service);
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

