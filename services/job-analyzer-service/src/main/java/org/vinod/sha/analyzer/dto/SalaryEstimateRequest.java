package org.vinod.sha.analyzer.dto;

import java.util.List;
import java.util.Objects;

/**
 * Java 21 Record: Request to estimate salary for a job.
 * Immutable data transfer object with automatic equals, hashCode, and toString.
 * 
 * Uses compact constructor for validation to ensure data integrity.
 */
public record SalaryEstimateRequest(
        String jobTitle,
        String location,
        Integer experienceYears,
        List<String> requiredSkills
) {
    // Compact constructor for validation
    public SalaryEstimateRequest {
        if (experienceYears != null && experienceYears < 0) {
            throw new IllegalArgumentException("Experience years cannot be negative");
        }
        // Ensure immutability by copying the list
        if (requiredSkills != null) {
            requiredSkills = List.copyOf(requiredSkills);
        }
    }
}

