package org.vinod.sha.notification.service;

import org.junit.jupiter.api.Test;
import org.vinod.sha.notification.config.InAppMessages;
import org.vinod.sha.notification.entity.InAppNotification;
import org.vinod.sha.notification.repository.InAppNotificationRepository;
import org.vinod.shared.events.CandidateMatchedEvent;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InAppNotificationServiceTest {

    private final InAppNotificationRepository repository = mock(InAppNotificationRepository.class);
    private final InAppNotificationService service = new InAppNotificationService(repository, new InAppMessages(Map.of(
            "SHORTLISTED", new InAppMessages.Message("You've been shortlisted for {job}", "Shortlisted for {job}."))));

    private static CandidateMatchedEvent event(String status) {
        return CandidateMatchedEvent.builder().matchId("m1").candidateId(32L).jobId("j1").status(status)
                .jobTitle("Principal Engineer").companyName("Acme").build();
    }

    @Test
    void shortlistTellsTheCandidateWhichJob() {
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        InAppNotification sent = service.onMatchStatus(event("SHORTLISTED")).orElseThrow();

        assertEquals(32L, sent.getUserId());
        assertEquals("You've been shortlisted for Principal Engineer at Acme", sent.getTitle());
        assertEquals("match:m1", sent.getReferenceId());
        assertFalse(sent.isRead());
    }

    @Test
    void statusesWithoutAMessageAreNotSent() {
        assertTrue(service.onMatchStatus(event("REJECTED")).isEmpty(), "rejections are not sent");
        assertTrue(service.onMatchStatus(event("MATCHED")).isEmpty());
        verify(repository, never()).save(any());
    }

    @Test
    void jobLabelWithoutCompany() {
        assertEquals("Principal Engineer", InAppNotificationService.jobLabel(
                CandidateMatchedEvent.builder().jobTitle("Principal Engineer").companyName("Unknown company").build()));
        assertEquals("a job", InAppNotificationService.jobLabel(CandidateMatchedEvent.builder().build()));
    }
}
