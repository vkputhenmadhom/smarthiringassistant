package org.vinod.sha.analyzer.dto;

import java.util.List;

/**
 * Java 21 Record: Request to estimate salary for a job.
 * Immutable data transfer object with automatic equals, hashCode, and toString.
 */
public record SalaryEstimateRequest(
        String jobTitle,
        String location,
        Integer experienceYears,
        List<String> requiredSkills
) {
}

