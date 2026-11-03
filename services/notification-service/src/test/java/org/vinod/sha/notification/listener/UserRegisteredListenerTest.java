package org.vinod.sha.notification.listener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vinod.sha.notification.service.NotificationService;
import org.vinod.shared.events.UserRegisteredEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegisteredListenerTest {

    @Mock  private NotificationService notificationService;
    @InjectMocks private UserRegisteredListener listener;

    @Test
    void onUserRegistered_newEvent_delegatesToService() {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(1L).username("alice").email("alice@example.com")
                .firstName("Alice").lastName("Wonder").role("JOB_SEEKER")
                .timestamp(System.currentTimeMillis()).build();

        when(notificationService.isAlreadyProcessed("UserRegisteredEvent:1")).thenReturn(false);

        listener.onUserRegistered(event);

        verify(notificationService).handleUserRegistered(event);
        verify(notificationService).markProcessed("UserRegisteredEvent:1", "UserRegisteredEvent");
    }

    @Test
    void onUserRegistered_duplicateEvent_skipsProcessing() {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(2L).username("bob").email("bob@example.com")
                .firstName("Bob").lastName("Builder").role("HR")
                .timestamp(System.currentTimeMillis()).build();

        when(notificationService.isAlreadyProcessed("UserRegisteredEvent:2")).thenReturn(true);

        listener.onUserRegistered(event);

        verify(notificationService, never()).handleUserRegistered(any());
    }

    @Test
    void onUserRegistered_exceptionInService_doesNotPropagate() {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(3L).username("carol").email("carol@example.com")
                .firstName("Carol").lastName("C").role("JOB_SEEKER")
                .timestamp(System.currentTimeMillis()).build();

        when(notificationService.isAlreadyProcessed(anyString())).thenReturn(false);
        doThrow(new RuntimeException("DB down"))
                .when(notificationService).handleUserRegistered(any());

        assertDoesNotThrow(() -> listener.onUserRegistered(event));
    }
}
