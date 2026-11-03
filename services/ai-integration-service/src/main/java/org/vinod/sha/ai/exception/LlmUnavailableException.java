package org.vinod.sha.ai.exception;

/** The LLM provider failed; the cause is logged, never returned to callers. */
public class LlmUnavailableException extends RuntimeException {
    public LlmUnavailableException(Throwable cause) {
        super("AI service temporarily unavailable", cause);
    }
}
