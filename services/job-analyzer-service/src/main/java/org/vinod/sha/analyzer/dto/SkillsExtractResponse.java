package org.vinod.sha.analyzer.dto;

import java.util.List;

/**
 * Java 21 Record: Response containing extracted skills.
 * Immutable data transfer object with automatic equals, hashCode, and toString.
 */
public record SkillsExtractResponse(
        List<String> requiredSkills,
        List<String> preferredSkills
) {
}

