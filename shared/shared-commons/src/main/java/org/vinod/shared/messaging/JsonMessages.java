package org.vinod.shared.messaging;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;

/** Builds AMQP messages from payloads that are already serialised JSON (e.g. outbox rows). */
public final class JsonMessages {

    private JsonMessages() {
    }

    /**
     * Sending a JSON string through {@code convertAndSend} labels it text/plain, which
     * JSON-converting consumers reject; this keeps the bytes and marks them application/json.
     */
    public static Message of(String json) {
        return MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }
}
