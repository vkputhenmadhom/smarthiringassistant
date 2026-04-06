package org.vinod.sha.notification.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.vinod.sha.notification.service.NotificationService;
import org.vinod.shared.events.UserRegisteredEvent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

/**
 * Tiny Spring wiring smoke test for one Rabbit listener component.
 * This validates listener bean wiring and delegation logic without requiring a live broker.
 */
@SpringJUnitConfig(UserRegisteredListenerSmokeIntegrationTest.TestConfig.class)
class UserRegisteredListenerSmokeIntegrationTest {

    @Configuration
    static class TestConfig {
        @Bean
        NotificationService notificationService() {
            return mock(NotificationService.class);
        }

        @Bean
        UserRegisteredListener userRegisteredListener(NotificationService notificationService) {
            return new UserRegisteredListener(notificationService);
        }
    }

    @jakarta.annotation.Resource
    private ApplicationContext applicationContext;

    @jakarta.annotation.Resource
    private UserRegisteredListener listener;

    @jakarta.annotation.Resource
    private NotificationService notificationService;

    @BeforeEach
    void resetMocks() {
        reset(notificationService);
    }

    @Test
    void contextLoads_listenerBeanPresent() {
        assertNotNull(applicationContext.getBean(UserRegisteredListener.class));
        assertNotNull(listener);
    }

    @Test
    void onUserRegistered_delegatesToNotificationService() {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(101L)
                .username("smoke-user")
                .email("smoke@example.com")
                .firstName("Smoke")
                .lastName("Test")
                .role("JOB_SEEKER")
                .timestamp(System.currentTimeMillis())
                .build();

        when(notificationService.isAlreadyProcessed("UserRegisteredEvent:101")).thenReturn(false);

        listener.onUserRegistered(event);

        verify(notificationService).handleUserRegistered(event);
        verify(notificationService).markProcessed("UserRegisteredEvent:101", "UserRegisteredEvent");
    }
}

