package org.vinod.sha.matcher.config;

import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.vinod.shared.config.RabbitMQConfiguration;

import static org.vinod.shared.config.SharedRabbitAutoConfiguration.consumerQueue;

/** The queue candidate-matcher consumes parsed resumes from. */
@Configuration
public class MatcherRabbitConfig {

    @Bean
    public Declarables resumeParsedQueue() {
        return consumerQueue(RabbitMQConfiguration.RESUME_PARSED_QUEUE,
                RabbitMQConfiguration.RESUME_EXCHANGE, RabbitMQConfiguration.RESUME_PARSED_ROUTING_KEY);
    }
}
