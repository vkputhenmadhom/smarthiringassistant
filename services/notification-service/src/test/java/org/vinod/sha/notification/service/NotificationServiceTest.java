package org.vinod.sha.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vinod.sha.notification.entity.NotificationLog;
import org.vinod.sha.notification.repository.NotificationLogRepository;
import org.vinod.sha.notification.repository.ProcessedEventRepository;
import org.vinod.shared.events.CandidateMatchedEvent;
import org.vinod.shared.events.ScreeningCompletedEvent;
import org.vinod.shared.events.UserRegisteredEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private EmailService emailService;
    @Mock private SmsService smsService;
    @Mock private NotificationLogRepository logRepository;
    @Mock private ProcessedEventRepository processedEventRepository;

    @InjectMocks private NotificationService service;


    @Test
    void handleUserRegistered_callsEmailServiceAndLogsEntry() {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(1L).username("john").email("john@example.com")
                .firstName("John").lastName("Doe").role("JOB_SEEKER")
                .timestamp(System.currentTimeMillis()).build();

        service.handleUserRegistered(event);

        verify(emailService).sendTemplatedEmail(
                eq("john@example.com"), anyString(), eq("welcome.ftl"), any());
        verify(logRepository).save(any(NotificationLog.class));
    }

    @Test
    void handleUserRegistered_emailSent_logsSentStatus() {
        when(emailService.sendTemplatedEmail(anyString(), anyString(), anyString(), any())).thenReturn(true);

        service.handleUserRegistered(UserRegisteredEvent.builder()
                .userId(2L).username("jane").email("jane@example.com").role("JOB_SEEKER").build());

        verify(logRepository).save(argThat(log -> "SENT".equals(log.getStatus())));
    }

    @Test
    void handleUserRegistered_emailDisabled_logsSkippedStatus() {
        when(emailService.sendTemplatedEmail(anyString(), anyString(), anyString(), any())).thenReturn(false);

        service.handleUserRegistered(UserRegisteredEvent.builder()
                .userId(3L).username("max").email("max@example.com").role("JOB_SEEKER").build());

        verify(logRepository).save(argThat(log -> "SKIPPED".equals(log.getStatus())));
    }

    @Test
    void handleUserRegistered_emailFailure_logsFailedStatus() {
        doThrow(new RuntimeException("SMTP error"))
                .when(emailService).sendTemplatedEmail(anyString(), anyString(), anyString(), any());

        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(2L).username("jane").email("jane@example.com")
                .firstName("Jane").lastName("Smith").role("HR")
                .timestamp(System.currentTimeMillis()).build();

        service.handleUserRegistered(event);

        verify(logRepository).save(argThat(log ->
                "FAILED".equals(log.getStatus()) &&
                "jane@example.com".equals(log.getRecipientEmail())));
    }

    @Test
    void handleCandidateMatched_logsSkipped() {
        CandidateMatchedEvent event = CandidateMatchedEvent.builder()
                .matchId("m1").candidateId(10L).jobId("j1")
                .matchScore(78.5).matchedAt(System.currentTimeMillis()).build();

        service.handleCandidateMatched(event);

        verify(logRepository).save(argThat(log -> "SKIPPED".equals(log.getStatus())));
        verifyNoInteractions(emailService);
    }

    @Test
    void handleScreeningCompleted_passDecision_logsSkipped() {
        ScreeningCompletedEvent event = ScreeningCompletedEvent.builder()
                .sessionId("s1").candidateId(5L).jobId("j2")
                .decision("PASS").finalScore(85.0)
                .completedAt(System.currentTimeMillis()).build();

        service.handleScreeningCompleted(event);

        verify(logRepository).save(any(NotificationLog.class));
    }

    @Test
    void isAlreadyProcessed_delegatesToRepository() {
        when(processedEventRepository.existsByEventKey("NS:TestEvent:99")).thenReturn(true);
        assertTrue(service.isAlreadyProcessed("TestEvent:99"));
    }

    @Test
    void markProcessed_savesWithNsPrefix() {
        service.markProcessed("TestEvent:42", "TestEvent");
        verify(processedEventRepository).save(argThat(pe ->
                "NS:TestEvent:42".equals(pe.getEventKey())));
    }
}
