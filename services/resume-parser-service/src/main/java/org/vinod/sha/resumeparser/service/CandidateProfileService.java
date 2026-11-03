package org.vinod.sha.resumeparser.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vinod.sha.resumeparser.dto.CandidateProfile;
import org.vinod.sha.resumeparser.entity.Education;
import org.vinod.sha.resumeparser.entity.Experience;
import org.vinod.sha.resumeparser.entity.ParsedResumeData;
import org.vinod.sha.resumeparser.entity.Resume;
import org.vinod.sha.resumeparser.repository.ResumeRepository;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Candidate profiles built from each user's latest resume. */
@Service
@Transactional(readOnly = true)
public class CandidateProfileService {

    private static final Pattern YEAR = Pattern.compile("(19|20)\\d{2}");

    private final ResumeRepository resumeRepository;

    public CandidateProfileService(ResumeRepository resumeRepository) {
        this.resumeRepository = resumeRepository;
    }

    /**
     * One page of candidates, newest resume first. Name and email are encrypted at rest, so a search
     * (name, email or skill) decrypts every candidate's latest resume and filters in memory.
     */
    public Page<CandidateProfile> list(int page, int size, String search) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 1000)));
        if (search == null || search.isBlank()) {
            return resumeRepository.findLatestPerUser(pageable).map(CandidateProfileService::toProfile);
        }
        String needle = search.trim().toLowerCase(Locale.ROOT);
        List<CandidateProfile> matching = resumeRepository.findLatestPerUser(Pageable.unpaged()).stream()
                .map(CandidateProfileService::toProfile)
                .filter(profile -> matches(profile, needle))
                .toList();
        int from = (int) Math.min(matching.size(), pageable.getOffset());
        int to = Math.min(matching.size(), from + pageable.getPageSize());
        return new PageImpl<>(matching.subList(from, to), pageable, matching.size());
    }

    public Optional<CandidateProfile> forUser(Long userId) {
        return resumeRepository.findFirstByUserIdOrderByCreatedAtDesc(userId).map(CandidateProfileService::toProfile);
    }

    public List<CandidateProfile> forUsers(Collection<Long> userIds) {
        return userIds.isEmpty() ? List.of()
                : resumeRepository.findLatestForUsers(userIds).stream().map(CandidateProfileService::toProfile).toList();
    }

    static CandidateProfile toProfile(Resume resume) {
        ParsedResumeData data = resume.getParsedData();
        return new CandidateProfile(
                resume.getUserId(),
                resume.getResumeId(),
                resume.getFileName(),
                data == null ? null : blankToNull(data.getFullName()),
                data == null ? null : blankToNull(data.getEmail()),
                data == null ? null : blankToNull(data.getPhone()),
                data == null ? null : blankToNull(data.getLocation()),
                data == null || data.getSkills() == null ? List.of() : List.copyOf(data.getSkills()),
                data == null ? null : data.getTotalExperienceYears(),
                data == null || data.getExperiences() == null ? List.of()
                        : data.getExperiences().stream().map(CandidateProfileService::toJob).toList(),
                data == null || data.getEducations() == null ? List.of()
                        : data.getEducations().stream().map(CandidateProfileService::toSchool).toList(),
                graphqlParseStatus(resume),
                Objects.toString(resume.getCreatedAt(), null),
                Objects.toString(resume.getUpdatedAt(), null));
    }

    /** The parser says SUCCESS; GraphQL's ParseStatus says COMPLETED. */
    static String graphqlParseStatus(Resume resume) {
        if (resume.getStatus() == null) {
            return "PENDING";
        }
        return switch (resume.getStatus()) {
            case SUCCESS -> "COMPLETED";
            case PROCESSING -> "PROCESSING";
            case FAILED -> "FAILED";
            case PENDING -> "PENDING";
        };
    }

    private static boolean matches(CandidateProfile profile, String needle) {
        return contains(profile.name(), needle) || contains(profile.email(), needle)
                || profile.skills().stream().anyMatch(skill -> contains(skill, needle));
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static CandidateProfile.Job toJob(Experience e) {
        return new CandidateProfile.Job(Objects.toString(e.getCompany(), ""), Objects.toString(e.getJobTitle(), ""),
                e.getStartDate(), e.getEndDate(), e.getDescription());
    }

    private static CandidateProfile.School toSchool(Education e) {
        Integer year = null;
        if (e.getGraduationDate() != null) {
            Matcher m = YEAR.matcher(e.getGraduationDate());
            if (m.find()) {
                year = Integer.valueOf(m.group());
            }
        }
        return new CandidateProfile.School(Objects.toString(e.getInstitution(), ""), Objects.toString(e.getDegree(), ""),
                e.getFieldOfStudy(), year);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
