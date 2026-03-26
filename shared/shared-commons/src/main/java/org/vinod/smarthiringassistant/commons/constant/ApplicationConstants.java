package org.vinod.smarthiringassistant.commons.constant;

/**
 * Application-wide constants
 */
public class ApplicationConstants {
    
    private ApplicationConstants() {
        throw new AssertionError("Cannot instantiate utility class");
    }
    
    // Service names
    public static final String RESUME_PARSER_SERVICE = "resume-parser-service";
    public static final String CANDIDATE_MATCHER_SERVICE = "candidate-matcher-service";
    public static final String INTERVIEW_PREP_SERVICE = "interview-prep-service";
    public static final String JOB_ANALYZER_SERVICE = "job-analyzer-service";
    public static final String SCREENING_BOT_SERVICE = "screening-bot-service";
    public static final String AI_INTEGRATION_SERVICE = "ai-integration-service";
    public static final String NOTIFICATION_SERVICE = "notification-service";
    public static final String AUTH_SERVICE = "auth-service";
    
    // Header constants
    public static final String CORRELATION_ID = "X-Correlation-ID";
    public static final String REQUEST_ID = "X-Request-ID";
    public static final String AUTHORIZATION = "Authorization";
    
    // Event constants
    public static final String EXCHANGE_NAME = "hiring.exchange";
    public static final String DLQ_EXCHANGE_NAME = "hiring.dlq.exchange";
    
    // Pagination constants
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    
    // Cache constants
    public static final String CACHE_PREFIX = "hiring:";
    public static final long CACHE_DEFAULT_TTL = 3600; // 1 hour
    
    // Role constants
    public static final String ROLE_HR = "ROLE_HR";
    public static final String ROLE_RECRUITER = "ROLE_RECRUITER";
    public static final String ROLE_CANDIDATE = "ROLE_CANDIDATE";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
}

