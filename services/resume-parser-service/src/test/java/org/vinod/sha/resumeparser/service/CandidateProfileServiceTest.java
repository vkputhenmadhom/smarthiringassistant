package org.vinod.sha.resumeparser.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.vinod.sha.resumeparser.dto.CandidateProfile;
import org.vinod.sha.resumeparser.entity.Education;
import org.vinod.sha.resumeparser.entity.Experience;
import org.vinod.sha.resumeparser.entity.ParseStatus;
import org.vinod.sha.resumeparser.entity.ParsedResumeData;
import org.vinod.sha.resumeparser.entity.Resume;
import org.vinod.sha.resumeparser.repository.ResumeRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CandidateProfileServiceTest {

    @Test
    void profileCarriesTheParsedResumeInGraphqlTerms() {
        CandidateProfile profile = CandidateProfileService.toProfile(resume(7L, "Jane Doe", "jane@example.com", List.of("Java", "Spring")));

        assertEquals(7L, profile.userId());
        assertEquals("Jane Doe", profile.name());
        assertEquals("COMPLETED", profile.parseStatus(), "the parser's SUCCESS is GraphQL's COMPLETED");
        assertEquals(List.of("Java", "Spring"), profile.skills());
        assertEquals("Engineer", profile.experience().get(0).title());
        assertEquals(2019, profile.education().get(0).graduationYear());
    }

    @Test
    void aResumeThatIsNotParsedYetHasNoDetails() {
        Resume pending = Resume.builder().userId(8L).resumeId("r-8").status(ParseStatus.PROCESSING)
                .createdAt(LocalDateTime.of(2026, 10, 30, 9, 0)).build();

        CandidateProfile profile = CandidateProfileService.toProfile(pending);

        assertEquals("PROCESSING", profile.parseStatus());
        assertNull(profile.name());
        assertEquals(List.of(), profile.skills());
    }

    @Test
    void searchMatchesDecryptedNamesEmailsAndSkillsAndPagesTheResult() {
        ResumeRepository repository = mock(ResumeRepository.class);
        List<Resume> all = List.of(
                resume(1L, "Jane Doe", "jane@example.com", List.of("Java")),
                resume(2L, "Raj Kumar", "raj@example.com", List.of("Kubernetes")),
                resume(3L, "Ana Lima", "ana@example.com", List.of("java", "Go")));
        when(repository.findLatestPerUser(any(Pageable.class))).thenReturn(new PageImpl<>(all));
        CandidateProfileService service = new CandidateProfileService(repository);

        Page<CandidateProfile> javaPeople = service.list(0, 1, "JAVA");

        assertEquals(2, javaPeople.getTotalElements());
        assertEquals(1L, javaPeople.getContent().get(0).userId());
        assertEquals(List.of(2L), service.list(0, 10, "raj@").map(CandidateProfile::userId).getContent());
    }

    private static Resume resume(Long userId, String name, String email, List<String> skills) {
        ParsedResumeData data = ParsedResumeData.builder().fullName(name).email(email).skills(skills)
                .experiences(List.of(Experience.builder().company("Acme").jobTitle("Engineer").startDate("2020").build()))
                .educations(List.of(Education.builder().institution("IIT").degree("BTech").graduationDate("May 2019").build()))
                .build();
        return Resume.builder().userId(userId).resumeId("r-" + userId).fileName(name + ".txt").status(ParseStatus.SUCCESS)
                .parsedData(data).createdAt(LocalDateTime.of(2026, 10, 30, 9, 0)).build();
    }
}
