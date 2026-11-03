package org.vinod.sha.resumeparser.dto;

import java.util.List;

/**
 * A candidate as HR and the gateway see them: the user plus what was parsed from their latest resume.
 * {@code parseStatus} uses the GraphQL names (PENDING, PROCESSING, COMPLETED, FAILED).
 */
public record CandidateProfile(
        Long userId,
        String resumeId,
        String fileName,
        String name,
        String email,
        String phone,
        String location,
        List<String> skills,
        Double totalExperienceYears,
        List<Job> experience,
        List<School> education,
        String parseStatus,
        String createdAt,
        String updatedAt) {

    public record Job(String company, String title, String startDate, String endDate, String description) {
    }

    public record School(String institution, String degree, String field, Integer graduationYear) {
    }
}
