package org.vinod.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SharedRabbitAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SharedRabbitAutoConfiguration.class));

    @Test
    void isRegisteredAsAnAutoConfigurationSoEveryServiceGetsIt() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates())
                .contains(SharedRabbitAutoConfiguration.class.getName());
    }

    @Test
    void providesAJsonMessageConverter() {
        runner.run(context -> assertThat(context.getBean(MessageConverter.class))
                .isInstanceOf(JacksonJsonMessageConverter.class));
    }

    @Test
    void backsOffWhenAServiceDefinesItsOwnConverter() {
        runner.withBean(MessageConverter.class, SimpleMessageConverter::new)
                .run(context -> assertThat(context.getBean(MessageConverter.class))
                        .isInstanceOf(SimpleMessageConverter.class));
    }

    @Test
    void declaresEveryExchangeAndTheDeadLetterQueue() {
        runner.run(context -> {
            Declarables topology = context.getBean("sharedRabbitTopology", Declarables.class);
            Set<String> exchanges = topology.getDeclarablesByType(Exchange.class).stream()
                    .map(Exchange::getName).collect(Collectors.toSet());
            assertThat(exchanges).containsExactlyInAnyOrder(
                    RabbitMQConfiguration.AUTH_EXCHANGE, RabbitMQConfiguration.RESUME_EXCHANGE,
                    RabbitMQConfiguration.MATCHER_EXCHANGE, RabbitMQConfiguration.JOB_EXCHANGE,
                    RabbitMQConfiguration.SCREENING_EXCHANGE, RabbitMQConfiguration.INTERVIEW_EXCHANGE,
                    RabbitMQConfiguration.DEAD_LETTER_EXCHANGE);
            assertThat(topology.getDeclarablesByType(Queue.class))
                    .extracting(Queue::getName).containsExactly(RabbitMQConfiguration.DEAD_LETTER_QUEUE);
        });
    }

    @Test
    void consumerQueuesAreDeadLetteredAndBound() {
        Declarables declarables = SharedRabbitAutoConfiguration.consumerQueue(
                "test.queue", RabbitMQConfiguration.MATCHER_EXCHANGE, RabbitMQConfiguration.CANDIDATE_MATCHED_ROUTING_KEY);

        Queue queue = declarables.getDeclarablesByType(Queue.class).getFirst();
        assertThat(queue.getArguments()).containsEntry("x-dead-letter-exchange", RabbitMQConfiguration.DEAD_LETTER_EXCHANGE);
        assertThat(declarables.getDeclarables()).hasSize(3);
    }
}
