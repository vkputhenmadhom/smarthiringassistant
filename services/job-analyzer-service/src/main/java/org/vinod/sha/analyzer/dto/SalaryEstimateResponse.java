package org.vinod.sha.analyzer.dto;

/**
 * Java 21 Record: Response containing salary estimation result.
 * Immutable data transfer object with automatic equals, hashCode, and toString.
 */
public record SalaryEstimateResponse(
        Integer min,
        Integer max,
        String currency,
        Double confidence,
        String rationale
) {
}

