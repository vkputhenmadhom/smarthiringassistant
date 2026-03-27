package org.vinod.sha.matcher.listener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vinod.sha.matcher.entity.ProcessedEvent;
import org.vinod.sha.matcher.repository.ProcessedEventRepository;
import org.vinod.sha.matcher.service.CandidateMatcherService;
import org.vinod.shared.events.ResumeParseEvent;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeParseEventListenerIntegrationTest {

    @Mock
    private CandidateMatcherService candidateMatcherService;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private ResumeParseEventListener listener;

    @Test
    void handleResumeParseEvent_success_invokesMatcherAndStoresProcessedMarker() {
        ResumeParseEvent event = ResumeParseEvent.builder()
                .resumeId("res-101")
                .userId(88L)
                .skills(List.of("Java", "Spring"))
                .experienceYears(5.0)
                .location("Remote")
                .status("SUCCESS")
                .build();

        when(processedEventRepository.existsByEventKey("ResumeParseEvent:res-101:SUCCESS")).thenReturn(false);
        when(candidateMatcherService.matchCandidateWithJobs(88L, List.of("Java", "Spring"), 5.0, "Remote"))
                .thenReturn(List.of());

        listener.handleResumeParseEvent(event);

        verify(candidateMatcherService).matchCandidateWithJobs(88L, List.of("Java", "Spring"), 5.0, "Remote");

        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(captor.capture());
        assertEquals("ResumeParseEvent:res-101:SUCCESS", captor.getValue().getEventKey());
        assertEquals("ResumeParseEvent", captor.getValue().getEventType());
    }

    @Test
    void handleResumeParseEvent_duplicate_doesNotProcessAgain() {
        ResumeParseEvent event = ResumeParseEvent.builder()
                .resumeId("res-dup")
                .status("SUCCESS")
                .build();

        when(processedEventRepository.existsByEventKey("ResumeParseEvent:res-dup:SUCCESS")).thenReturn(true);

        listener.handleResumeParseEvent(event);

        verify(candidateMatcherService, never()).matchCandidateWithJobs(any(), any(), any(), any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void handleResumeParseEvent_failedStatus_skipsMatchingButStoresMarker() {
        ResumeParseEvent event = ResumeParseEvent.builder()
                .resumeId("res-fail")
                .status("FAILED")
                .errorMessage("Could not parse")
                .build();

        when(processedEventRepository.existsByEventKey("ResumeParseEvent:res-fail:FAILED")).thenReturn(false);

        listener.handleResumeParseEvent(event);

        verify(candidateMatcherService, never()).matchCandidateWithJobs(any(), any(), any(), any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }
}

