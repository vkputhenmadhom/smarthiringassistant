package org.vinod.sha.analyzer.dto;

/**
 * Java 21 Record: Request to extract skills from text.
 * Immutable data transfer object with automatic equals, hashCode, and toString.
 * 
 * Uses compact constructor for validation to ensure data integrity.
 */
public record SkillsExtractRequest(String text) {
    // Compact constructor for validation
    public SkillsExtractRequest {
        if (text != null && text.isBlank()) {
            throw new IllegalArgumentException("Text cannot be blank");
        }
    }
}

