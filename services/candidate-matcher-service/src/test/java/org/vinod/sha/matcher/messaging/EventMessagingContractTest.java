package org.vinod.sha.matcher.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.vinod.shared.events.CandidateMatchedEvent;
import org.vinod.shared.events.ResumeParseEvent;
import org.vinod.shared.events.ScreeningCompletedEvent;
import org.vinod.shared.messaging.JsonMessages;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The resume -> matcher -> screening pipeline's wire format: each event is produced the way its
 * producer produces it and read back the way a typed {@code @RabbitListener} reads it.
 */
class EventMessagingContractTest {

    /** What every service gets from SharedRabbitAutoConfiguration. */
    private final MessageConverter converter = new JacksonJsonMessageConverter();
    /** OutboxPublisher in resume-parser and candidate-matcher serialises with Jackson 2. */
    private final ObjectMapper outboxMapper = new ObjectMapper();

    @Test
    void resumeParsedOutboxEventReachesTheMatcher() throws Exception {
        ResumeParseEvent sent = ResumeParseEvent.builder()
                .resumeId("r-11").userId(7L).status("SUCCESS")
                .skills(List.of("java", "spring")).experienceYears(6.0).location("Pune")
                .build();

        assertEquals(sent, receive(outboxMessage(sent), ResumeParseEvent.class));
    }

    @Test
    void candidateMatchedOutboxEventReachesScreeningNotificationAndGateway() throws Exception {
        CandidateMatchedEvent sent = CandidateMatchedEvent.builder()
                .matchId("m-1").candidateId(7L).jobId("job-9").matchScore(82.5).matchedAt(1_700_000_000_000L)
                .build();

        assertEquals(sent, receive(outboxMessage(sent), CandidateMatchedEvent.class));
    }

    @Test
    void screeningCompletedEventPublishedWithConvertAndSendIsReadable() {
        ScreeningCompletedEvent sent = ScreeningCompletedEvent.builder()
                .sessionId("s-1").candidateId(7L).jobId("job-9").decision("PASS").finalScore(91.0)
                .completedAt(1_700_000_000_000L)
                .build();

        // screening-bot publishes the object itself through the RabbitTemplate's converter
        Message message = converter.toMessage(sent, new MessageProperties());

        assertEquals(MessageProperties.CONTENT_TYPE_JSON, message.getMessageProperties().getContentType());
        assertEquals(sent, receive(message, ScreeningCompletedEvent.class));
    }

    private Message outboxMessage(Object event) throws Exception {
        return JsonMessages.of(outboxMapper.writeValueAsString(event));
    }

    private <T> T receive(Message message, Class<T> listenerParameterType) {
        // The listener container tells the converter the @RabbitListener parameter type
        message.getMessageProperties().setInferredArgumentType(listenerParameterType);
        return listenerParameterType.cast(converter.fromMessage(message));
    }
}
