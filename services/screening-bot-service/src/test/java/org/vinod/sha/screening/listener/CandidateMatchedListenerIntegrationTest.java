package org.vinod.sha.screening.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vinod.sha.screening.dto.CreateSessionRequest;
import org.vinod.sha.screening.entity.ConsumedEvent;
import org.vinod.sha.screening.repository.ConsumedEventRepository;
import org.vinod.sha.screening.service.ScreeningBotService;
import org.vinod.shared.events.CandidateMatchedEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateMatchedListenerIntegrationTest {

    @Mock
    private ScreeningBotService screeningBotService;

    @Mock
    private ConsumedEventRepository consumedEventRepository;

    private CandidateMatchedListener listener;

    @BeforeEach
    void autoStartOn() {
        listener = new CandidateMatchedListener(screeningBotService, consumedEventRepository, true, 50);
    }

    @Test
    void onCandidateMatched_autoStartOff_onlyStoresMarker() {
        CandidateMatchedListener off = new CandidateMatchedListener(screeningBotService, consumedEventRepository, false, 50);
        when(consumedEventRepository.existsByEventKey("CandidateMatchedEvent:m-off")).thenReturn(false);

        off.onCandidateMatched(CandidateMatchedEvent.builder().matchId("m-off").candidateId(9L).jobId("JOB-1").matchScore(95.0).build());

        verify(screeningBotService, never()).createSession(any(CreateSessionRequest.class));
        verify(consumedEventRepository).save(any(ConsumedEvent.class));
    }

    @Test
    void onCandidateMatched_highScore_createsSessionAndStoresConsumedMarker() {
        CandidateMatchedEvent event = CandidateMatchedEvent.builder()
                .matchId("m-1")
                .candidateId(9L)
                .jobId("JOB-42")
                .matchScore(76.5)
                .build();

        when(consumedEventRepository.existsByEventKey("CandidateMatchedEvent:m-1")).thenReturn(false);

        listener.onCandidateMatched(event);

        ArgumentCaptor<CreateSessionRequest> requestCaptor = ArgumentCaptor.forClass(CreateSessionRequest.class);
        verify(screeningBotService).createSession(requestCaptor.capture());
        assertEquals(9L, requestCaptor.getValue().getCandidateId());
        assertEquals("JOB-42", requestCaptor.getValue().getJobId());

        ArgumentCaptor<ConsumedEvent> consumedCaptor = ArgumentCaptor.forClass(ConsumedEvent.class);
        verify(consumedEventRepository).save(consumedCaptor.capture());
        assertEquals("CandidateMatchedEvent:m-1", consumedCaptor.getValue().getEventKey());
        assertEquals("CandidateMatchedEvent", consumedCaptor.getValue().getEventType());
        assertNotNull(consumedCaptor.getValue().getConsumedAt());
    }

    @Test
    void onCandidateMatched_lowScore_doesNotCreateSessionButStillStoresMarker() {
        CandidateMatchedEvent event = CandidateMatchedEvent.builder()
                .matchId("m-2")
                .candidateId(12L)
                .jobId("JOB-13")
                .matchScore(49.9)
                .build();

        when(consumedEventRepository.existsByEventKey("CandidateMatchedEvent:m-2")).thenReturn(false);

        listener.onCandidateMatched(event);

        verify(screeningBotService, never()).createSession(any(CreateSessionRequest.class));
        verify(consumedEventRepository).save(any(ConsumedEvent.class));
    }

    @Test
    void onCandidateMatched_duplicate_skipsProcessing() {
        CandidateMatchedEvent event = CandidateMatchedEvent.builder()
                .matchId("m-dup")
                .matchScore(80.0)
                .build();

        when(consumedEventRepository.existsByEventKey("CandidateMatchedEvent:m-dup")).thenReturn(true);

        listener.onCandidateMatched(event);

        verify(screeningBotService, never()).createSession(any(CreateSessionRequest.class));
        verify(consumedEventRepository, never()).save(any(ConsumedEvent.class));
    }
}

