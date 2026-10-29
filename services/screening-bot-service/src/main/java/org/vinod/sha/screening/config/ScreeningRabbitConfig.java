package org.vinod.sha.screening.config;

import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.vinod.shared.config.RabbitMQConfiguration;

import static org.vinod.shared.config.SharedRabbitAutoConfiguration.consumerQueue;

/** screening-bot's own copy of candidate-matched events. */
@Configuration
public class ScreeningRabbitConfig {

    @Bean
    public Declarables candidateMatchedQueue() {
        return consumerQueue(RabbitMQConfiguration.CANDIDATE_MATCHED_SCREENING_QUEUE,
                RabbitMQConfiguration.MATCHER_EXCHANGE, RabbitMQConfiguration.CANDIDATE_MATCHED_ROUTING_KEY);
    }
}
