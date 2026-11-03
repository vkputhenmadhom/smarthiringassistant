package org.vinod.sha.ai.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.vinod.sha.ai.dto.AIResponse;

/** Maps failures to fixed, client-safe messages; provider and stack details stay in the logs. */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<AIResponse> rateLimited(RateLimitExceededException e) {
        return error(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(LlmUnavailableException.class)
    ResponseEntity<AIResponse> unavailable(LlmUnavailableException e) {
        log.error("LLM provider call failed", e.getCause());
        return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<AIResponse> badRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<AIResponse> invalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Invalid request");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<AIResponse> unexpected(Exception e) {
        log.error("Unexpected error in AI integration service", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private static ResponseEntity<AIResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(AIResponse.builder().error(message).build());
    }
}
