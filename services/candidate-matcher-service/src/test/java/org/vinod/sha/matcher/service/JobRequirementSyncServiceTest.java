package org.vinod.sha.matcher.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.vinod.sha.matcher.entity.JobRequirement;
import org.vinod.sha.matcher.entity.JobStatus;
import org.vinod.sha.matcher.repository.JobRequirementRepository;
import org.vinod.shared.events.JobPostingChangedEvent;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JobRequirementSyncServiceTest {

    private final JobRequirementRepository repository = mock(JobRequirementRepository.class);
    private final JobRequirementSyncService service = new JobRequirementSyncService(repository);

    @Test
    void createsAMatchableJobFromAnOpenPosting() {
        when(repository.findByJobId("job-1")).thenReturn(Optional.empty());

        service.apply(posting("job-1", "OPEN").minExperienceYears(4).requiredSkills(List.of("java", "kafka")).build());

        JobRequirement saved = saved();
        assertEquals("job-1", saved.getJobId());
        assertEquals("Backend Engineer", saved.getJobTitle());
        assertEquals(List.of("java", "kafka"), saved.getRequiredSkills());
        assertEquals(4, saved.getRequiredYearsExperience());
        assertEquals(JobStatus.ACTIVE, saved.getStatus());
    }

    @Test
    void anOpenJobOutsideTheJobMarketIsKeptButNeverMatched() {
        when(repository.findByJobId("us-1")).thenReturn(Optional.empty());

        service.apply(posting("us-1", "OPEN").inMarket(false).build());

        assertEquals(JobStatus.ON_HOLD, saved().getStatus());
    }

    @Test
    void eventsFromOlderProducersWithoutTheMarketFlagCountAsInMarket() {
        when(repository.findByJobId("job-2")).thenReturn(Optional.empty());

        service.apply(posting("job-2", "OPEN").build());

        assertEquals(JobStatus.ACTIVE, saved().getStatus());
    }

    @Test
    void updatesTheExistingCopyInsteadOfCreatingAnother() {
        JobRequirement existing = new JobRequirement();
        existing.setId(10L);
        existing.setJobId("job-1");
        when(repository.findByJobId("job-1")).thenReturn(Optional.of(existing));

        service.apply(posting("job-1", "CLOSED").build());

        JobRequirement saved = saved();
        assertSame(existing, saved);
        assertEquals(JobStatus.CLOSED, saved.getStatus());
    }

    @Test
    void mapsJobAnalyzerStatusesToMatcherStatuses() {
        assertEquals(JobStatus.ACTIVE, JobRequirementSyncService.toMatcherStatus("OPEN"));
        assertEquals(JobStatus.CLOSED, JobRequirementSyncService.toMatcherStatus("CLOSED"));
        assertEquals(JobStatus.ON_HOLD, JobRequirementSyncService.toMatcherStatus("DRAFT"));
        assertEquals(JobStatus.ON_HOLD, JobRequirementSyncService.toMatcherStatus("ANALYZED"));
        assertEquals(JobStatus.ON_HOLD, JobRequirementSyncService.toMatcherStatus(null));
    }

    @Test
    void fillsRequiredColumnsWhenThePostingLacksThem() {
        when(repository.findByJobId("job-2")).thenReturn(Optional.empty());

        service.apply(JobPostingChangedEvent.builder().jobId("job-2").status("OPEN").location("x".repeat(400)).build());

        JobRequirement saved = saved();
        assertEquals("Untitled job", saved.getJobTitle());
        assertEquals("Unknown company", saved.getCompanyName());
        assertEquals(0, saved.getRequiredYearsExperience());
        assertEquals(List.of(), saved.getRequiredSkills());
        assertEquals(255, saved.getLocation().length());
    }

    @Test
    void removesDeletedJobsAndIgnoresUnknownOnes() {
        JobRequirement existing = new JobRequirement();
        when(repository.findByJobId("job-1")).thenReturn(Optional.of(existing));
        when(repository.findByJobId("gone")).thenReturn(Optional.empty());

        service.apply(JobPostingChangedEvent.builder().jobId("job-1").deleted(true).build());
        service.apply(JobPostingChangedEvent.builder().jobId("gone").deleted(true).build());

        verify(repository).delete(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void ignoresEventsWithoutAJobId() {
        service.apply(JobPostingChangedEvent.builder().status("OPEN").build());

        verifyNoInteractions(repository);
    }

    private static JobPostingChangedEvent.JobPostingChangedEventBuilder posting(String id, String status) {
        return JobPostingChangedEvent.builder().jobId(id).status(status)
                .jobTitle("Backend Engineer").companyName("Acme").location("Pune").jobDescription("Build services");
    }

    private JobRequirement saved() {
        ArgumentCaptor<JobRequirement> captor = ArgumentCaptor.forClass(JobRequirement.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
