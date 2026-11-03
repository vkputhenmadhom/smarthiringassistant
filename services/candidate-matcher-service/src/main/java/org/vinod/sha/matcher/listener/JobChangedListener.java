package org.vinod.sha.matcher.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.vinod.sha.matcher.service.JobRequirementSyncService;
import org.vinod.shared.config.RabbitMQConfiguration;
import org.vinod.shared.events.JobPostingChangedEvent;

/** Applies job-analyzer's job changes to the jobs candidate-matcher matches against. */
@Component
public class JobChangedListener {

    private final JobRequirementSyncService syncService;

    public JobChangedListener(JobRequirementSyncService syncService) {
        this.syncService = syncService;
    }

    @RabbitListener(queues = RabbitMQConfiguration.JOB_CHANGED_MATCHER_QUEUE)
    public void onJobChanged(JobPostingChangedEvent event) {
        syncService.apply(event);
    }
}
