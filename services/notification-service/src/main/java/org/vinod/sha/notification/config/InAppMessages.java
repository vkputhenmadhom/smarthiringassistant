package org.vinod.sha.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * In-app messages for a candidate's match status (application.yaml, {@code notification.in-app.match-status}):
 * only the statuses listed are sent. {@code {job}} becomes "Principal Engineer at Acme".
 */
@ConfigurationProperties("notification.in-app")
public record InAppMessages(Map<String, Message> matchStatus) {

    public InAppMessages {
        matchStatus = matchStatus == null ? Map.of() : Map.copyOf(matchStatus);
    }

    public record Message(String title, String message) {
    }
}
