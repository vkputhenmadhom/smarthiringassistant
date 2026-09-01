package org.vinod.sha.analyzer.dto;

/**
 * Java 21 Record: Request to analyze a job posting.
 * Immutable data transfer object with automatic equals, hashCode, and toString.
 * 
 * Uses compact constructor for validation to ensure data integrity.
 */
public record JobAnalyzeRequest(
        String jobId,
        String jobTitle,
        String companyName,
        String location,
        String employmentType,
        String jobDescription
) {
    // Compact constructor for validation
    public JobAnalyzeRequest {
        if (jobTitle != null && jobTitle.isBlank()) {
            throw new IllegalArgumentException("Job title cannot be blank");
        }
        if (jobDescription != null && jobDescription.isBlank()) {
            throw new IllegalArgumentException("Job description cannot be blank");
        }
    }
}

