package org.vinod.sha.analyzer.dto;

/**
 * Java 21 Record: Request to analyze a job posting.
 * Immutable data transfer object with automatic equals, hashCode, and toString.
 */
public record JobAnalyzeRequest(
        String jobId,
        String jobTitle,
        String companyName,
        String location,
        String employmentType,
        String jobDescription
) {
}

