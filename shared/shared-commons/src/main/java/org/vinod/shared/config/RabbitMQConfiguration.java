package org.vinod.shared.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfiguration {

    public static final String DEAD_LETTER_EXCHANGE = "events.dlx";
    public static final String DEAD_LETTER_QUEUE = "events.dlq";

    // Auth Service Exchanges and Queues
    public static final String AUTH_EXCHANGE = "auth.exchange";
    public static final String USER_REGISTERED_QUEUE = "user.registered.queue";
    public static final String USER_AUTHENTICATED_QUEUE = "user.authenticated.queue";
    public static final String USER_REGISTERED_ROUTING_KEY = "user.registered";
    public static final String USER_AUTHENTICATED_ROUTING_KEY = "user.authenticated";

    // Resume Parser Exchanges and Queues
    public static final String RESUME_EXCHANGE = "resume.exchange";
    public static final String RESUME_PARSED_QUEUE = "resume.parsed.queue";
    public static final String RESUME_PARSED_ROUTING_KEY = "resume.parsed";

    // Candidate Matcher Exchanges and Queues
    public static final String MATCHER_EXCHANGE = "matcher.exchange";
    public static final String CANDIDATE_MATCHED_QUEUE = "candidate.matched.queue";
    public static final String CANDIDATE_MATCHED_ROUTING_KEY = "candidate.matched";

    // Job Analyzer Exchanges and Queues
    public static final String JOB_EXCHANGE = "job.exchange";
    public static final String JOB_ANALYZED_QUEUE = "job.analyzed.queue";
    public static final String JOB_ANALYZED_ROUTING_KEY = "job.analyzed";

    // Screening Bot Exchanges and Queues
    public static final String SCREENING_EXCHANGE = "screening.exchange";
    public static final String SCREENING_COMPLETED_QUEUE = "screening.completed.queue";
    public static final String SCREENING_COMPLETED_ROUTING_KEY = "screening.completed";
    public static final String SCREENING_COMPENSATION_QUEUE = "screening.compensation.queue";
    public static final String SCREENING_COMPENSATION_ROUTING_KEY = "screening.compensation";

    // Auth Exchange
    @Bean
    public TopicExchange authExchange() {
        return new TopicExchange(AUTH_EXCHANGE, true, false);
    }

    @Bean
    public Queue userRegisteredQueue() {
        return QueueBuilder.durable(USER_REGISTERED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Queue userAuthenticatedQueue() {
        return QueueBuilder.durable(USER_AUTHENTICATED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding userRegisteredBinding(Queue userRegisteredQueue, TopicExchange authExchange) {
        return BindingBuilder.bind(userRegisteredQueue)
                .to(authExchange)
                .with(USER_REGISTERED_ROUTING_KEY);
    }

    @Bean
    public Binding userAuthenticatedBinding(Queue userAuthenticatedQueue, TopicExchange authExchange) {
        return BindingBuilder.bind(userAuthenticatedQueue)
                .to(authExchange)
                .with(USER_AUTHENTICATED_ROUTING_KEY);
    }

    // Resume Exchange
    @Bean
    public TopicExchange resumeExchange() {
        return new TopicExchange(RESUME_EXCHANGE, true, false);
    }

    @Bean
    public Queue resumeParsedQueue() {
        return QueueBuilder.durable(RESUME_PARSED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding resumeParsedBinding(Queue resumeParsedQueue, TopicExchange resumeExchange) {
        return BindingBuilder.bind(resumeParsedQueue)
                .to(resumeExchange)
                .with(RESUME_PARSED_ROUTING_KEY);
    }

    // Matcher Exchange
    @Bean
    public TopicExchange matcherExchange() {
        return new TopicExchange(MATCHER_EXCHANGE, true, false);
    }

    @Bean
    public Queue candidateMatchedQueue() {
        return QueueBuilder.durable(CANDIDATE_MATCHED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding candidateMatchedBinding(Queue candidateMatchedQueue, TopicExchange matcherExchange) {
        return BindingBuilder.bind(candidateMatchedQueue)
                .to(matcherExchange)
                .with(CANDIDATE_MATCHED_ROUTING_KEY);
    }

    // Job Analyzer Exchange
    @Bean
    public TopicExchange jobExchange() {
        return new TopicExchange(JOB_EXCHANGE, true, false);
    }

    @Bean
    public Queue jobAnalyzedQueue() {
        return QueueBuilder.durable(JOB_ANALYZED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding jobAnalyzedBinding(Queue jobAnalyzedQueue, TopicExchange jobExchange) {
        return BindingBuilder.bind(jobAnalyzedQueue)
                .to(jobExchange)
                .with(JOB_ANALYZED_ROUTING_KEY);
    }

    // Screening Exchange
    @Bean
    public TopicExchange screeningExchange() {
        return new TopicExchange(SCREENING_EXCHANGE, true, false);
    }

    @Bean
    public Queue screeningCompletedQueue() {
        return QueueBuilder.durable(SCREENING_COMPLETED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with("#");
    }

    @Bean
    public Binding screeningCompletedBinding(Queue screeningCompletedQueue, TopicExchange screeningExchange) {
        return BindingBuilder.bind(screeningCompletedQueue)
                .to(screeningExchange)
                .with(SCREENING_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public Queue screeningCompensationQueue() {
        return QueueBuilder.durable(SCREENING_COMPENSATION_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding screeningCompensationBinding(Queue screeningCompensationQueue, TopicExchange screeningExchange) {
        return BindingBuilder.bind(screeningCompensationQueue)
                .to(screeningExchange)
                .with(SCREENING_COMPENSATION_ROUTING_KEY);
    }

    // Rabbit message converter for JSON serialization
    @Bean
    public org.springframework.amqp.support.converter.MessageConverter jsonMessageConverter() {
        return new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter();
    }
}

