package org.vinod.sha.matcher.config;

import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.vinod.shared.config.RabbitMQConfiguration;

import static org.vinod.shared.config.SharedRabbitAutoConfiguration.consumerQueue;

/** The queues candidate-matcher consumes: parsed resumes and job changes. */
@Configuration
public class MatcherRabbitConfig {

    @Bean
    public Declarables resumeParsedQueue() {
        return consumerQueue(RabbitMQConfiguration.RESUME_PARSED_QUEUE,
                RabbitMQConfiguration.RESUME_EXCHANGE, RabbitMQConfiguration.RESUME_PARSED_ROUTING_KEY);
    }

    @Bean
    public Declarables jobChangedQueue() {
        return consumerQueue(RabbitMQConfiguration.JOB_CHANGED_MATCHER_QUEUE,
                RabbitMQConfiguration.JOB_EXCHANGE, RabbitMQConfiguration.JOB_CHANGED_ROUTING_KEY);
    }
}
