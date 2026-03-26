package org.vinod.sha.matcher.algorithm;

import java.util.List;
import java.util.Map;

public interface MatchingAlgorithm {
    /**
     * Calculate match score between candidate skills and job requirements
     * @return score between 0 and 100
     */
    double calculateScore(List<String> candidateSkills, List<String> requiredSkills);
}

