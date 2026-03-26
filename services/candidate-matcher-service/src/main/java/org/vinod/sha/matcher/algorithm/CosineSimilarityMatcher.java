package org.vinod.sha.matcher.algorithm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Calculates match score using cosine similarity (TF-IDF based)
 */
@Slf4j
@Component
public class CosineSimilarityMatcher implements MatchingAlgorithm {

    @Override
    public double calculateScore(List<String> candidateSkills, List<String> requiredSkills) {
        if (candidateSkills == null || requiredSkills == null ||
            candidateSkills.isEmpty() || requiredSkills.isEmpty()) {
            return 0.0;
        }

        Set<String> candidateSet = candidateSkills.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        Set<String> requiredSet = requiredSkills.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        // Calculate intersection
        Set<String> intersection = candidateSet.stream()
                .filter(requiredSet::contains)
                .collect(Collectors.toSet());

        // Cosine similarity formula
        double dotProduct = intersection.size();
        double magnitude = Math.sqrt(candidateSet.size() * requiredSet.size());

        if (magnitude == 0) {
            return 0.0;
        }

        double similarity = dotProduct / magnitude;
        return Math.min(similarity * 100, 100.0); // Convert to percentage
    }
}

