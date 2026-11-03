package org.vinod.sha.ai.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException() {
        super("Rate limit exceeded. Please try again later.");
    }
}
