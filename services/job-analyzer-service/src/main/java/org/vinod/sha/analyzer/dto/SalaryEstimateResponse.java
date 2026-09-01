package org.vinod.sha.analyzer.dto;

/**
 * Java 21 Record: Response containing salary estimation result.
 * Immutable data transfer object with automatic equals, hashCode, and toString.
 * 
 * Uses compact constructor for validation to ensure data integrity.
 */
public record SalaryEstimateResponse(
        Integer min,
        Integer max,
        String currency,
        Double confidence,
        String rationale
) {
    // Compact constructor for validation
    public SalaryEstimateResponse {
        if (min != null && min < 0) {
            throw new IllegalArgumentException("Minimum salary cannot be negative");
        }
        if (max != null && max < 0) {
            throw new IllegalArgumentException("Maximum salary cannot be negative");
        }
        if (min != null && max != null && min > max) {
            throw new IllegalArgumentException("Minimum salary must be <= maximum salary");
        }
        if (confidence != null && (confidence < 0 || confidence > 1)) {
            throw new IllegalArgumentException("Confidence must be between 0 and 1");
        }
    }
}

