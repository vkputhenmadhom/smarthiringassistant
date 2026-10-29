package org.vinod.sha.notification.config;

import org.springframework.amqp.core.Declarables;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.vinod.shared.config.RabbitMQConfiguration;

import static org.vinod.shared.config.SharedRabbitAutoConfiguration.consumerQueue;

/**
 * notification-service's own queues. The JSON converter, exchanges and dead-letter queue come
 * from shared-commons (SharedRabbitAutoConfiguration).
 */
@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationRabbitConfig {

    public static final String INTERVIEW_SCHEDULED_NOTIFICATION_QUEUE = "interview.scheduled.notification.queue";
    public static final String INTERVIEW_SCHEDULED_ROUTING_KEY = "interview.scheduled";

    @Bean
    public Declarables userRegisteredQueue() {
        return consumerQueue(RabbitMQConfiguration.USER_REGISTERED_QUEUE,
                RabbitMQConfiguration.AUTH_EXCHANGE, RabbitMQConfiguration.USER_REGISTERED_ROUTING_KEY);
    }

    @Bean
    public Declarables candidateMatchedQueue() {
        return consumerQueue(RabbitMQConfiguration.CANDIDATE_MATCHED_NOTIFICATION_QUEUE,
                RabbitMQConfiguration.MATCHER_EXCHANGE, RabbitMQConfiguration.CANDIDATE_MATCHED_ROUTING_KEY);
    }

    @Bean
    public Declarables screeningCompletedQueue() {
        return consumerQueue(RabbitMQConfiguration.SCREENING_COMPLETED_NOTIFICATION_QUEUE,
                RabbitMQConfiguration.SCREENING_EXCHANGE, RabbitMQConfiguration.SCREENING_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public Declarables interviewScheduledQueue() {
        return consumerQueue(INTERVIEW_SCHEDULED_NOTIFICATION_QUEUE,
                RabbitMQConfiguration.INTERVIEW_EXCHANGE, INTERVIEW_SCHEDULED_ROUTING_KEY);
    }
}
