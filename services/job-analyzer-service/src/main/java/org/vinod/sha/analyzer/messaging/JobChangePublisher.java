package org.vinod.sha.analyzer.messaging;

import org.vinod.sha.analyzer.service.JobMarket;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterDeleteEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.stereotype.Component;
import org.vinod.sha.analyzer.entity.JobAnalysis;
import org.vinod.sha.analyzer.repository.JobAnalysisRepository;
import org.vinod.shared.config.RabbitMQConfiguration;
import org.vinod.shared.events.JobPostingChangedEvent;

/**
 * Publishes a {@link JobPostingChangedEvent} whenever a job document is saved or deleted, whichever
 * code path did it (HR edits, analysis, job-board imports), so candidate-matcher's copy stays current.
 * Publishing is best-effort: a broker outage is logged and never fails the save. {@link #republishAll()}
 * re-sends every job, e.g. to fill an empty matcher database.
 */
@Slf4j
@Component
public class JobChangePublisher extends AbstractMongoEventListener<JobAnalysis> {

    private final RabbitTemplate rabbitTemplate;
    private final JobAnalysisRepository repository;
    private final JobMarket jobMarket;

    public JobChangePublisher(RabbitTemplate rabbitTemplate, JobAnalysisRepository repository, JobMarket jobMarket) {
        this.rabbitTemplate = rabbitTemplate;
        this.repository = repository;
        this.jobMarket = jobMarket;
    }

    @Override
    public void onAfterSave(AfterSaveEvent<JobAnalysis> event) {
        JobAnalysis job = event.getSource();
        publish(toEvent(job, jobMarket.includes(job)));
    }

    @Override
    public void onAfterDelete(AfterDeleteEvent<JobAnalysis> event) {
        Document source = event.getSource();
        Object id = source == null ? null : source.get("_id");
        if (id == null) {
            log.warn("Job deleted without an id in the delete query; matcher copy not updated: {}", source);
            return;
        }
        publish(JobPostingChangedEvent.builder()
                .jobId(id.toString())
                .deleted(true)
                .changedAt(System.currentTimeMillis())
                .build());
    }

    /** Re-sends every job as an upsert. Returns how many were published. */
    public int republishAll() {
        int published = 0;
        for (JobAnalysis job : repository.findAll()) {
            if (publish(toEvent(job, jobMarket.includes(job)))) {
                published++;
            }
        }
        log.info("Republished {} job(s) to candidate-matcher", published);
        return published;
    }

    /** @param inMarket false keeps the job out of matching (see {@link JobMarket}) */
    static JobPostingChangedEvent toEvent(JobAnalysis job, boolean inMarket) {
        return JobPostingChangedEvent.builder()
                .jobId(job.getId())
                .jobTitle(job.getJobTitle())
                .companyName(job.getCompanyName())
                .location(job.getLocation())
                .jobDescription(job.getJobDescription())
                .requiredSkills(job.getRequiredSkills())
                .minExperienceYears(job.getMinExperienceYears())
                .status(job.getStatus())
                .inMarket(inMarket)
                .changedAt(System.currentTimeMillis())
                .build();
    }

    private boolean publish(JobPostingChangedEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfiguration.JOB_EXCHANGE,
                    RabbitMQConfiguration.JOB_CHANGED_ROUTING_KEY, event);
            return true;
        } catch (AmqpException e) {
            log.warn("Could not publish JobPostingChangedEvent for job {}: {}", event.getJobId(), e.getMessage());
            return false;
        }
    }
}
