package org.vinod.shared.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Messaging defaults every service gets from shared-commons (registered as a Spring Boot
 * auto-configuration, so it applies regardless of a service's component-scan packages):
 * <ul>
 *   <li>a JSON {@link MessageConverter}, used by the auto-configured RabbitTemplate and listener
 *       containers, so typed {@code @RabbitListener} methods can read events</li>
 *   <li>every exchange plus the dead-letter exchange and queue, so a publisher never sends to an
 *       exchange that does not exist yet on a fresh broker</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(RabbitTemplate.class)
public class SharedRabbitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public Declarables sharedRabbitTopology() {
        TopicExchange deadLetterExchange = topic(RabbitMQConfiguration.DEAD_LETTER_EXCHANGE);
        Queue deadLetterQueue = QueueBuilder.durable(RabbitMQConfiguration.DEAD_LETTER_QUEUE).build();
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with("#");
        return new Declarables(List.of(
                topic(RabbitMQConfiguration.AUTH_EXCHANGE),
                topic(RabbitMQConfiguration.RESUME_EXCHANGE),
                topic(RabbitMQConfiguration.MATCHER_EXCHANGE),
                topic(RabbitMQConfiguration.JOB_EXCHANGE),
                topic(RabbitMQConfiguration.SCREENING_EXCHANGE),
                topic(RabbitMQConfiguration.INTERVIEW_EXCHANGE),
                deadLetterExchange, deadLetterQueue, deadLetterBinding));
    }

    /**
     * Declares a consumer-owned, dead-lettered queue bound to an exchange. Consuming services call
     * this from their own configuration so they get their own copy of every event.
     */
    public static Declarables consumerQueue(String queue, String exchange, String routingKey) {
        Queue q = QueueBuilder.durable(queue).deadLetterExchange(RabbitMQConfiguration.DEAD_LETTER_EXCHANGE).build();
        TopicExchange x = topic(exchange);
        return new Declarables(q, x, BindingBuilder.bind(q).to(x).with(routingKey));
    }

    private static TopicExchange topic(String name) {
        return new TopicExchange(name, true, false);
    }
}
