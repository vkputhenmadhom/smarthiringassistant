package org.vinod.sha.analytics.bridge.messaging;

import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.vinod.shared.config.RabbitMQConfiguration;

import static org.vinod.shared.config.SharedRabbitAutoConfiguration.consumerQueue;

/** analytics-bridge's own copies of the events it forwards to Kafka. */
@Configuration
public class AnalyticsRabbitConfig {

    @Bean
    public Declarables candidateMatchedQueue() {
        return consumerQueue(RabbitMQConfiguration.CANDIDATE_MATCHED_ANALYTICS_QUEUE,
                RabbitMQConfiguration.MATCHER_EXCHANGE, RabbitMQConfiguration.CANDIDATE_MATCHED_ROUTING_KEY);
    }

    @Bean
    public Declarables screeningCompletedQueue() {
        return consumerQueue(RabbitMQConfiguration.SCREENING_COMPLETED_ANALYTICS_QUEUE,
                RabbitMQConfiguration.SCREENING_EXCHANGE, RabbitMQConfiguration.SCREENING_COMPLETED_ROUTING_KEY);
    }
}
