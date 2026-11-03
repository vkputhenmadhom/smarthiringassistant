package org.vinod.sha.matcher.service;

import org.junit.jupiter.api.Test;
import org.vinod.sha.matcher.algorithm.WeightedScoreMatcher;
import org.vinod.sha.matcher.entity.CandidateMatch;
import org.vinod.sha.matcher.entity.JobRequirement;
import org.vinod.sha.matcher.entity.JobStatus;
import org.vinod.sha.matcher.entity.MatchStatus;
import org.vinod.sha.matcher.outbox.OutboxPublisher;
import org.vinod.sha.matcher.repository.CandidateMatchRepository;
import org.vinod.sha.matcher.repository.JobRequirementRepository;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchCutoffAndCapTest {

    private final CandidateMatchRepository matches = mock(CandidateMatchRepository.class);
    private final JobRequirementRepository jobs = mock(JobRequirementRepository.class);

    MatchCutoffAndCapTest() {
        when(matches.save(any())).thenAnswer(call -> {
            CandidateMatch saved = call.getArgument(0);
            saved.setStatus(MatchStatus.MATCHED); // set by @PrePersist in the database
            return saved;
        });
    }

    private CandidateMatcherService service(int cap) {
        return new CandidateMatcherService(matches, jobs, new WeightedScoreMatcher(org.vinod.sha.matcher.config.MatchingRules.defaults()), mock(OutboxPublisher.class), 50, cap);
    }

    private static JobRequirement job(String id, List<String> skills, String title, JobStatus status) {
        return JobRequirement.builder()
                .jobId(id).jobTitle(title).companyName("Acme")
                .requiredSkills(skills).requiredYearsExperience(0)
                .status(status)
                .build();
    }

    @Test
    void keepsOnlyRelevantJobsAboveTheCutoffBestFirst() {
        when(jobs.findAll()).thenReturn(List.of(
                job("sales", List.of(), "Sales Executive", JobStatus.ACTIVE),            // nothing relevant
                job("half", List.of("Java", "SQL", "Kafka", "Go"), "Engineer", JobStatus.ACTIVE), // 50: kept
                job("full", List.of("Java", "Spring", "SQL"), "Engineer", JobStatus.ACTIVE),     // 100: kept first
                job("weak", List.of("Java", "Go", "Kafka"), "Engineer", JobStatus.ACTIVE),       // 33: below cutoff
                job("single", List.of("Java"), "Engineer", JobStatus.ACTIVE),                    // 1 of at least 3: below cutoff
                job("closed", List.of("Java", "Spring", "SQL"), "Engineer", JobStatus.CLOSED)));

        List<CandidateMatch> result = service(20).matchCandidateWithJobs(7L, List.of("Java", "Spring", "SQL"), 4.0, "Pune");

        assertEquals(List.of("full", "half"), result.stream().map(CandidateMatch::getJobId).toList());
        assertNull(result.get(0).getExperienceMatchPercentage());
        assertNull(result.get(0).getLocationMatchPercentage());
        assertEquals(List.of("Kafka", "Go"), result.get(1).getMissingSkills());
    }

    @Test
    void newResumeReplacesUntouchedMatchesAndKeepsOnesHrActedOn() {
        CandidateMatch untouched = CandidateMatch.builder().matchId("old").candidateId(7L).jobId("a").status(MatchStatus.MATCHED).build();
        CandidateMatch shortlisted = CandidateMatch.builder().matchId("hr").candidateId(7L).jobId("b").status(MatchStatus.SHORTLISTED).build();
        when(matches.findByCandidateId(7L)).thenReturn(List.of(untouched, shortlisted));
        when(jobs.findAll()).thenReturn(List.of(
                job("a", List.of("Java", "Spring", "SQL"), "Engineer", JobStatus.ACTIVE),
                job("b", List.of("Java", "Spring", "SQL"), "Engineer", JobStatus.ACTIVE)));

        List<CandidateMatch> result = service(20).matchCandidateWithJobs(7L, List.of("Java", "Spring", "SQL"), null, null);

        verify(matches).deleteAll(List.of(untouched));
        assertEquals(List.of("a"), result.stream().map(CandidateMatch::getJobId).toList(), "job b keeps its shortlisted match");
    }

    @Test
    void capsMatchesPerResume() {
        List<JobRequirement> many = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            many.add(job("job-" + i, List.of("Java", "Spring", "SQL"), "Engineer", JobStatus.ACTIVE));
        }
        when(jobs.findAll()).thenReturn(many);

        assertEquals(20, service(20).matchCandidateWithJobs(7L, List.of("Java", "Spring", "SQL"), null, null).size());
    }

    @Test
    void jobWithoutSkillListRecordsTheSkillsItMentions() {
        when(jobs.findAll()).thenReturn(List.of(
                job("desc", List.of(), "Java Developer", JobStatus.ACTIVE)));
        var found = service(20).matchCandidateWithJobs(7L, List.of("Java", "SQL"), null, "Pune");

        // Only one of the candidate's skills is mentioned: 33, below the cutoff on its own.
        assertEquals(List.of(), found);

        JobRequirement described = job("desc", List.of(), "Java Developer", JobStatus.ACTIVE);
        described.setJobDescription("Java, SQL and Docker on AWS");
        described.setLocation("Pune");
        when(jobs.findAll()).thenReturn(List.of(described));
        var match = service(20).matchCandidateWithJobs(7L, List.of("Java", "SQL", "Docker"), null, "Pune").get(0);

        assertEquals(List.of("Java", "SQL", "Docker"), match.getMatchedSkills());
        assertEquals(List.of(), match.getMissingSkills());
    }
}
