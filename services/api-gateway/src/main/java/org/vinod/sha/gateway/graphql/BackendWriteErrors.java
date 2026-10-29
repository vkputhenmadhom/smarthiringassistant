package org.vinod.sha.gateway.graphql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * Turns a failed backend write into a GraphQL error the client can show. Writes must not fall back to a
 * placeholder (a stub job, session or match, or {@code false}): the client would treat the change as made
 * when it was not.
 */
@Slf4j
final class BackendWriteErrors {

    private BackendWriteErrors() {
    }

    /**
     * @param service     the backend as users know it, e.g. "job service"
     * @param action      what was attempted, e.g. "create"
     * @param target      what it was attempted on, e.g. "the job" or "job 6ae3"
     * @param refreshHint how to check whether a timed-out write went through, e.g. "Refresh the job list"
     */
    static ClientFacingException of(String service, String action, String target, String refreshHint, Throwable error) {
        if (error instanceof ClientFacingException clientFacing) {
            return clientFacing;
        }
        if (error instanceof WebClientResponseException response && response.getStatusCode().is4xxClientError()) {
            if (response.getStatusCode().value() == 404) {
                return new ClientFacingException(ErrorType.NOT_FOUND, "Could not " + action + " " + target + ": it no longer exists.");
            }
            String body = response.getResponseBodyAsString(StandardCharsets.UTF_8).trim();
            // Spring's JSON error documents are not readable to a user; plain-text reasons are.
            String reason = body.isEmpty() || body.startsWith("{") || body.length() > 300
                    ? "the " + service + " rejected it (" + response.getStatusCode().value() + ")"
                    : body;
            return new ClientFacingException(ErrorType.BAD_REQUEST, "Could not " + action + " " + target + ": " + reason);
        }
        log.warn("{} {} failed for {}: {}", service, action, target, error.toString());
        if (Exceptions.unwrap(error) instanceof TimeoutException || error.getCause() instanceof TimeoutException) {
            // The request reached the backend and may still complete there; a blind retry could duplicate it.
            return new ClientFacingException(ErrorType.INTERNAL_ERROR, "The " + service + " did not answer in time, so your "
                    + "change may still go through. " + refreshHint + " before trying again.");
        }
        return new ClientFacingException(ErrorType.INTERNAL_ERROR,
                "Could not " + action + " " + target + ": the " + service + " is unavailable. Please try again shortly.");
    }
}
