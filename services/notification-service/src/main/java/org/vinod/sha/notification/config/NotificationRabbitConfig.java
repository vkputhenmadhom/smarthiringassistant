package org.vinod.sha.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.vinod.shared.config.RabbitMQConfiguration;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationRabbitConfig {

    public static final String INTERVIEW_EXCHANGE = "interview.exchange";
    public static final String INTERVIEW_SCHEDULED_NOTIFICATION_QUEUE = "interview.scheduled.notification.queue";
    public static final String INTERVIEW_SCHEDULED_ROUTING_KEY = "interview.scheduled";

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public TopicExchange interviewExchange() {
        return new TopicExchange(INTERVIEW_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange authExchange() {
        return new TopicExchange(RabbitMQConfiguration.AUTH_EXCHANGE, true, false);
    }

    @Bean
    public Queue userRegisteredQueue() {
        return QueueBuilder.durable(RabbitMQConfiguration.USER_REGISTERED_QUEUE)
                .deadLetterExchange(RabbitMQConfiguration.DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding userRegisteredBinding(
            @Qualifier("userRegisteredQueue") Queue userRegisteredQueue,
            @Qualifier("authExchange") TopicExchange authExchange) {
        return BindingBuilder.bind(userRegisteredQueue)
                .to(authExchange)
                .with(RabbitMQConfiguration.USER_REGISTERED_ROUTING_KEY);
    }

    @Bean
    public TopicExchange matcherExchange() {
        return new TopicExchange(RabbitMQConfiguration.MATCHER_EXCHANGE, true, false);
    }

    @Bean
    public Queue candidateMatchedQueue() {
        return QueueBuilder.durable(RabbitMQConfiguration.CANDIDATE_MATCHED_QUEUE)
                .deadLetterExchange(RabbitMQConfiguration.DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding candidateMatchedBinding(
            @Qualifier("candidateMatchedQueue") Queue candidateMatchedQueue,
            @Qualifier("matcherExchange") TopicExchange matcherExchange) {
        return BindingBuilder.bind(candidateMatchedQueue)
                .to(matcherExchange)
                .with(RabbitMQConfiguration.CANDIDATE_MATCHED_ROUTING_KEY);
    }

    @Bean
    public TopicExchange screeningExchange() {
        return new TopicExchange(RabbitMQConfiguration.SCREENING_EXCHANGE, true, false);
    }

    @Bean
    public Queue screeningCompletedQueue() {
        return QueueBuilder.durable(RabbitMQConfiguration.SCREENING_COMPLETED_QUEUE)
                .deadLetterExchange(RabbitMQConfiguration.DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding screeningCompletedBinding(
            @Qualifier("screeningCompletedQueue") Queue screeningCompletedQueue,
            @Qualifier("screeningExchange") TopicExchange screeningExchange) {
        return BindingBuilder.bind(screeningCompletedQueue)
                .to(screeningExchange)
                .with(RabbitMQConfiguration.SCREENING_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public Queue interviewScheduledNotificationQueue() {
        return QueueBuilder.durable(INTERVIEW_SCHEDULED_NOTIFICATION_QUEUE)
                .deadLetterExchange(RabbitMQConfiguration.DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding interviewScheduledNotificationBinding(
            @Qualifier("interviewScheduledNotificationQueue") Queue queue,
            @Qualifier("interviewExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(INTERVIEW_SCHEDULED_ROUTING_KEY);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        return factory;
    }
}
