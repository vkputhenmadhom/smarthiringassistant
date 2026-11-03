package org.vinod.shared.config;

/**
 * RabbitMQ topology names shared by every service.
 * <p>
 * Exchanges and the dead-letter queue are declared for every service by
 * {@link SharedRabbitAutoConfiguration}. Queues are owned by their consumer: each consuming
 * service declares and binds its own queue, so every consumer receives every event instead of
 * competing with other services for messages on a shared queue.
 */
public final class RabbitMQConfiguration {

    public static final String DEAD_LETTER_EXCHANGE = "events.dlx";
    public static final String DEAD_LETTER_QUEUE = "events.dlq";

    // Auth
    public static final String AUTH_EXCHANGE = "auth.exchange";
    public static final String USER_REGISTERED_ROUTING_KEY = "user.registered";
    public static final String USER_AUTHENTICATED_ROUTING_KEY = "user.authenticated";
    /** Consumed by notification-service. */
    public static final String USER_REGISTERED_QUEUE = "user.registered.queue";

    // Resume parser
    public static final String RESUME_EXCHANGE = "resume.exchange";
    public static final String RESUME_PARSED_ROUTING_KEY = "resume.parsed";
    /** Consumed by candidate-matcher-service. */
    public static final String RESUME_PARSED_QUEUE = "resume.parsed.queue";

    // Candidate matcher
    public static final String MATCHER_EXCHANGE = "matcher.exchange";
    public static final String CANDIDATE_MATCHED_ROUTING_KEY = "candidate.matched";
    public static final String CANDIDATE_MATCHED_SCREENING_QUEUE = "screening.candidate.matched.queue";
    public static final String CANDIDATE_MATCHED_NOTIFICATION_QUEUE = "notification.candidate.matched.queue";
    public static final String CANDIDATE_MATCHED_ANALYTICS_QUEUE = "analytics.candidate.matched.queue";

    // Job analyzer
    public static final String JOB_EXCHANGE = "job.exchange";
    public static final String JOB_ANALYZED_ROUTING_KEY = "job.analyzed";
    /** Job created, updated or deleted (JobPostingChangedEvent). */
    public static final String JOB_CHANGED_ROUTING_KEY = "job.changed";
    /** Consumed by candidate-matcher-service to keep its job copy in sync. */
    public static final String JOB_CHANGED_MATCHER_QUEUE = "matcher.job.changed.queue";

    // Screening bot
    public static final String SCREENING_EXCHANGE = "screening.exchange";
    public static final String SCREENING_COMPLETED_ROUTING_KEY = "screening.completed";
    public static final String SCREENING_COMPENSATION_ROUTING_KEY = "screening.compensation";
    public static final String SCREENING_COMPLETED_NOTIFICATION_QUEUE = "notification.screening.completed.queue";
    public static final String SCREENING_COMPLETED_ANALYTICS_QUEUE = "analytics.screening.completed.queue";

    // Interview scheduling
    public static final String INTERVIEW_EXCHANGE = "interview.exchange";

    private RabbitMQConfiguration() {
    }
}
