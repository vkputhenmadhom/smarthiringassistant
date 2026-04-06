package org.vinod.sha.notification.listener;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.vinod.sha.notification.NotificationServiceApplication;
import org.vinod.sha.notification.repository.NotificationLogRepository;
import org.vinod.shared.config.RabbitMQConfiguration;
import org.vinod.shared.events.UserRegisteredEvent;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Broker-backed smoke test:
 * publishes one UserRegisteredEvent to RabbitMQ and verifies notification log persistence.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = NotificationServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(RabbitMQConfiguration.class)
@ActiveProfiles("smoke")
class UserRegisteredListenerBrokerSmokeTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("smart_hiring_db")
            .withUsername("hiring_user")
            .withPassword("hiring_password")
            .withInitScript("init-smoke.sql");

    @Container
    static final RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbitmq.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private NotificationLogRepository logRepository;

    @Test
    void publishUserRegisteredEvent_persistsNotificationLog() throws Exception {
        long id = System.currentTimeMillis();
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(id)
                .username("smoke-user-" + id)
                .email("smoke-" + id + "@example.com")
                .firstName("Smoke")
                .lastName("Test")
                .role("JOB_SEEKER")
                .timestamp(System.currentTimeMillis())
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfiguration.AUTH_EXCHANGE,
                RabbitMQConfiguration.USER_REGISTERED_ROUTING_KEY,
                event
        );

        String ref = "user:" + id;
        boolean persisted = false;
        for (int i = 0; i < 40; i++) {
            if (!logRepository.findByReferenceId(ref).isEmpty()) {
                persisted = true;
                break;
            }
            Thread.sleep(250);
        }

        assertTrue(persisted, "Expected notification log to be persisted for reference " + ref);
    }
}

