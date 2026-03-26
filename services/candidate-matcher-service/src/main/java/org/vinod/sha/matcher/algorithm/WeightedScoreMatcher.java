package org.vinod.sha.matcher.algorithm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Weighted scoring algorithm combining multiple factors
 * - Skill match: 40%
 * - Experience match: 30%
 * - Location match: 30%
 */
@Slf4j
@Component
public class WeightedScoreMatcher {

    private static final double SKILL_WEIGHT = 0.40;
    private static final double EXPERIENCE_WEIGHT = 0.30;
    private static final double LOCATION_WEIGHT = 0.30;

    private final CosineSimilarityMatcher skillMatcher;

    public WeightedScoreMatcher(CosineSimilarityMatcher skillMatcher) {
        this.skillMatcher = skillMatcher;
    }

    public double calculateWeightedScore(
            List<String> candidateSkills,
            List<String> requiredSkills,
            Double candidateExperience,
            Integer requiredExperience,
            String candidateLocation,
            String jobLocation) {

        // Calculate skill match score
        double skillScore = skillMatcher.calculateScore(candidateSkills, requiredSkills);

        // Calculate experience match score
        double experienceScore = calculateExperienceScore(candidateExperience, requiredExperience);

        // Calculate location match score
        double locationScore = calculateLocationScore(candidateLocation, jobLocation);

        // Calculate weighted average
        double weightedScore = (skillScore * SKILL_WEIGHT) +
                              (experienceScore * EXPERIENCE_WEIGHT) +
                              (locationScore * LOCATION_WEIGHT);

        return Math.min(weightedScore, 100.0);
    }

    private double calculateExperienceScore(Double candidateExperience, Integer requiredExperience) {
        if (candidateExperience == null || requiredExperience == null) {
            return 0.0;
        }

        if (candidateExperience >= requiredExperience) {
            return 100.0;
        }

        // Partial credit for less experience
        return (candidateExperience / requiredExperience) * 100;
    }

    private double calculateLocationScore(String candidateLocation, String jobLocation) {
        if (candidateLocation == null || jobLocation == null ||
            candidateLocation.isEmpty() || jobLocation.isEmpty()) {
            return 0.0;
        }

        // Exact match
        if (candidateLocation.equalsIgnoreCase(jobLocation)) {
            return 100.0;
        }

        // Partial match (same state/country)
        if (candidateLocation.toLowerCase().contains(jobLocation.toLowerCase()) ||
            jobLocation.toLowerCase().contains(candidateLocation.toLowerCase())) {
            return 50.0;
        }

        // Remote friendly
        if (candidateLocation.toLowerCase().contains("remote") ||
            jobLocation.toLowerCase().contains("remote")) {
            return 75.0;
        }

        return 0.0;
    }
}

