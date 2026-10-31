package org.vinod.sha.matcher.algorithm;

import org.springframework.stereotype.Component;
import org.vinod.shared.text.SkillText;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Weighted scoring over what the job actually states:
 * - Skills: 40%
 * - Experience: 30%
 * - Location: 30%
 *
 * A part the job (or candidate) leaves unknown is left out and the remaining weights are rescaled,
 * instead of being scored as a free 100 or a 0. Skills are never left out: with no relevant skill
 * the candidate is not a match at all.
 */
@Component
public class WeightedScoreMatcher {

    private static final double SKILL_WEIGHT = 0.40;
    private static final double EXPERIENCE_WEIGHT = 0.30;
    private static final double LOCATION_WEIGHT = 0.30;

    /** A job that lists no skills scores this many of the candidate's skills found in its title or description as full marks. */
    private static final int DESCRIPTION_MENTIONS_FOR_FULL_SKILL_SCORE = 3;

    /**
     * @param overall     0–100 over the known parts
     * @param skills      0–100; 0 means no relevant skill, which is never a match
     * @param experience  0–100, or null when the job states no experience or the candidate's is unknown
     * @param location    0–100, or null when either location is unknown
     */
    public record Score(double overall, double skills, Double experience, Double location) {
        public boolean hasRelevantSkill() {
            return skills > 0;
        }
    }

    public Score score(List<String> candidateSkills,
                       List<String> requiredSkills,
                       String jobTitle,
                       String jobDescription,
                       Double candidateExperience,
                       Integer requiredExperience,
                       String candidateLocation,
                       String jobLocation) {

        double skills = skillScore(candidateSkills, requiredSkills, jobTitle, jobDescription);
        Double experience = experienceScore(candidateExperience, requiredExperience);
        Double location = locationScore(candidateLocation, jobLocation);

        double weighted = skills * SKILL_WEIGHT;
        double weights = SKILL_WEIGHT;
        if (experience != null) {
            weighted += experience * EXPERIENCE_WEIGHT;
            weights += EXPERIENCE_WEIGHT;
        }
        if (location != null) {
            weighted += location * LOCATION_WEIGHT;
            weights += LOCATION_WEIGHT;
        }
        double overall = skills == 0 ? 0 : Math.min(weighted / weights, 100.0);
        return new Score(overall, skills, experience, location);
    }

    /**
     * Share of the job's required skills the candidate has. A job that lists none is judged by how many
     * of the candidate's skills its title or description mentions as whole words.
     */
    static double skillScore(List<String> candidateSkills, List<String> requiredSkills,
                             String jobTitle, String jobDescription) {
        Set<String> candidate = normalized(candidateSkills);
        if (candidate.isEmpty()) {
            return 0.0;
        }
        Set<String> required = normalized(requiredSkills);
        if (!required.isEmpty()) {
            long have = required.stream().filter(candidate::contains).count();
            return have * 100.0 / required.size();
        }

        String text = (jobTitle == null ? "" : jobTitle) + "\n" + (jobDescription == null ? "" : jobDescription);
        long mentioned = candidate.stream().filter(skill -> SkillText.mentions(text, skill)).count();
        return Math.min(mentioned, DESCRIPTION_MENTIONS_FOR_FULL_SKILL_SCORE) * 100.0 / DESCRIPTION_MENTIONS_FOR_FULL_SKILL_SCORE;
    }

    /** Jobs are stored with 0 years when the posting states none, so 0 means unknown here. */
    static Double experienceScore(Double candidateExperience, Integer requiredExperience) {
        if (candidateExperience == null || requiredExperience == null || requiredExperience <= 0) {
            return null;
        }
        if (candidateExperience >= requiredExperience) {
            return 100.0;
        }
        return Math.max(0.0, candidateExperience / requiredExperience * 100);
    }

    static Double locationScore(String candidateLocation, String jobLocation) {
        if (candidateLocation == null || jobLocation == null ||
            candidateLocation.isBlank() || jobLocation.isBlank()) {
            return null;
        }
        String candidate = candidateLocation.trim().toLowerCase(Locale.ROOT);
        String job = jobLocation.trim().toLowerCase(Locale.ROOT);

        if (candidate.equals(job)) {
            return 100.0;
        }
        // Partial match (same city, state or country)
        if (candidate.contains(job) || job.contains(candidate)) {
            return 50.0;
        }
        // Remote friendly
        if (candidate.contains("remote") || job.contains("remote")) {
            return 75.0;
        }
        return 0.0;
    }

    private static Set<String> normalized(List<String> skills) {
        return skills == null ? Set.of() : skills.stream()
                .filter(Objects::nonNull)
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
