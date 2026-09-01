package org.vinod.sha.analyzer.dto;

import java.util.List;

/**
 * Java 21 Record: Response containing extracted skills.
 * Immutable data transfer object with automatic equals, hashCode, and toString.
 * 
 * Uses compact constructor for validation to ensure data integrity.
 */
public record SkillsExtractResponse(
        List<String> requiredSkills,
        List<String> preferredSkills
) {
    // Compact constructor for validation
    public SkillsExtractResponse {
        // Ensure immutability by copying the lists
        if (requiredSkills != null) {
            requiredSkills = List.copyOf(requiredSkills);
        } else {
            requiredSkills = List.of();
        }
        if (preferredSkills != null) {
            preferredSkills = List.copyOf(preferredSkills);
        } else {
            preferredSkills = List.of();
        }
    }
}

