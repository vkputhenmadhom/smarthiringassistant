package org.vinod.sha.analyzer.messaging;

import org.vinod.sha.analyzer.service.JobMarket;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.mongodb.core.mapping.event.AfterDeleteEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.vinod.sha.analyzer.entity.JobAnalysis;
import org.vinod.sha.analyzer.repository.JobAnalysisRepository;
import org.vinod.shared.config.RabbitMQConfiguration;
import org.vinod.shared.events.JobPostingChangedEvent;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobChangePublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final JobAnalysisRepository repository = mock(JobAnalysisRepository.class);
    private final JobChangePublisher publisher = new JobChangePublisher(rabbitTemplate, repository, new JobMarket("IN"));

    @Test
    void publishesAnUpsertWithTheDocumentIdWhenAJobIsSaved() {
        JobAnalysis job = JobAnalysis.builder().id("65f0c0ffee").jobId("random-uuid").jobTitle("Backend Engineer")
                .companyName("Acme").requiredSkills(List.of("java")).minExperienceYears(3).status("OPEN").build();

        publisher.onAfterSave(new AfterSaveEvent<>(job, new Document(), "job_analysis"));

        JobPostingChangedEvent event = sent();
        assertEquals("65f0c0ffee", event.getJobId(), "the id the frontends and GraphQL use, not the internal jobId");
        assertFalse(event.isDeleted());
        assertEquals("OPEN", event.getStatus());
        assertEquals(List.of("java"), event.getRequiredSkills());
        assertEquals(3, event.getMinExperienceYears());
    }

    @Test
    void marksJobsOutsideTheJobMarketSoTheMatcherSkipsThem() {
        publisher.onAfterSave(new AfterSaveEvent<>(JobAnalysis.builder().id("us").source("JSEARCH").status("OPEN").build(),
                new Document(), "job_analysis"));
        assertEquals(Boolean.FALSE, sent().getInMarket(), "a US import while the market is India");
    }

    @Test
    void indiaImportsAndInAppPostingsAreInTheIndianMarket() {
        JobMarket india = new JobMarket("IN");

        assertTrue(india.includes(JobAnalysis.builder().source("JSEARCH_TECH_IN").build()));
        assertTrue(india.includes(JobAnalysis.builder().source("adzuna_govt_in").build()));
        assertTrue(india.includes(JobAnalysis.builder().source("JSEARCH_REMOTE_IN").build()), "remote jobs open to India");
        assertTrue(india.includes(JobAnalysis.builder().build()), "posted by HR in the app");
        assertFalse(india.includes(JobAnalysis.builder().source("JSEARCH").build()));
        assertTrue(new JobMarket("").includes(JobAnalysis.builder().source("JSEARCH").build()), "blank market = every job");
    }

    @Test
    void publishesADeleteWithTheIdFromTheDeleteQuery() {
        ObjectId id = new ObjectId();

        publisher.onAfterDelete(new AfterDeleteEvent<>(new Document("_id", id), JobAnalysis.class, "job_analysis"));

        JobPostingChangedEvent event = sent();
        assertEquals(id.toHexString(), event.getJobId());
        assertTrue(event.isDeleted());
    }

    @Test
    void aBrokerOutageNeverFailsTheSave() {
        doThrow(new AmqpConnectException(new java.net.ConnectException("refused")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        assertDoesNotThrow(() -> publisher.onAfterSave(
                new AfterSaveEvent<>(JobAnalysis.builder().id("1").status("OPEN").build(), new Document(), "job_analysis")));
    }

    @Test
    void republishesEveryJob() {
        when(repository.findAll()).thenReturn(List.of(
                JobAnalysis.builder().id("1").status("OPEN").build(),
                JobAnalysis.builder().id("2").status("CLOSED").build()));

        assertEquals(2, publisher.republishAll());
        verify(rabbitTemplate, times(2)).convertAndSend(eq(RabbitMQConfiguration.JOB_EXCHANGE),
                eq(RabbitMQConfiguration.JOB_CHANGED_ROUTING_KEY), any(JobPostingChangedEvent.class));
    }

    private JobPostingChangedEvent sent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfiguration.JOB_EXCHANGE),
                eq(RabbitMQConfiguration.JOB_CHANGED_ROUTING_KEY), captor.capture());
        return (JobPostingChangedEvent) captor.getValue();
    }
}
