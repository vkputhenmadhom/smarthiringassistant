package org.vinod.sha.matcher.algorithm;

import org.springframework.stereotype.Component;
import org.vinod.sha.matcher.config.MatchingRules;
import org.vinod.shared.text.SkillText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Weighted scoring over what the job actually states:
 * - Skills: 40%
 * - Experience: 30%
 * - Location: 30%
 *
 * A part the job (or candidate) leaves unknown is left out and the remaining weights are rescaled,
 * instead of being scored as a free 100 or a 0. Skills are never left out: with no relevant skill
 * the candidate is not a match at all. Aliases and the skill floor come from {@link MatchingRules}.
 */
@Component
public class WeightedScoreMatcher {

    private static final double SKILL_WEIGHT = 0.40;
    private static final double EXPERIENCE_WEIGHT = 0.30;
    private static final double LOCATION_WEIGHT = 0.30;

    /**
     * A candidate skill the job's title or description names, but its skill list does not, counts this much of
     * a listed one: imported jobs list few skills, yet their text says more.
     */
    private static final double MENTION_WEIGHT = 0.5;

    private final MatchingRules rules;

    public WeightedScoreMatcher(MatchingRules rules) {
        this.rules = rules;
    }

    /**
     * @param overall     0–100 over the known parts
     * @param skills      0–100; 0 means no relevant skill, which is never a match
     * @param experience  0–100: how the candidate's years fit the job's range (stated, or its title's level, from
     *                    job-analyzer); null when the job's range or the candidate's years are unknown
     * @param location    0–100, or null when either side's location is unknown
     * @param matched     the candidate's skills the job lists or names
     * @param missing     skills the job lists that the candidate does not have
     */
    public record Score(double overall, double skills, Double experience, Double location,
                        List<String> matched, List<String> missing) {
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
                       Integer maxExperience,
                       String candidateLocation,
                       List<String> preferredLocations,
                       String jobLocation) {

        SkillFit fit = skillFit(candidateSkills, requiredSkills, jobTitle, jobDescription);
        Double experience = experienceScore(candidateExperience, requiredExperience, maxExperience);
        Double location = locationScore(candidateLocation, preferredLocations, jobLocation);

        double weighted = fit.score() * SKILL_WEIGHT;
        double weights = SKILL_WEIGHT;
        if (experience != null) {
            weighted += experience * EXPERIENCE_WEIGHT;
            weights += EXPERIENCE_WEIGHT;
        }
        if (location != null) {
            weighted += location * LOCATION_WEIGHT;
            weights += LOCATION_WEIGHT;
        }
        double overall = fit.score() == 0 ? 0 : Math.min(weighted / weights, 100.0);
        return new Score(overall, fit.score(), experience, location, fit.matched(), fit.missing());
    }

    record SkillFit(double score, List<String> matched, List<String> missing) {}

    /**
     * Listed skills the candidate has count fully, other candidate skills the title or description names count
     * {@value #MENTION_WEIGHT}; the total is divided by the number of listed skills, but by at least
     * {@code matching.min-required-skills}.
     */
    SkillFit skillFit(List<String> candidateSkills, List<String> requiredSkills, String jobTitle, String jobDescription) {
        Map<String, String> candidate = canonical(candidateSkills);
        Map<String, String> required = canonical(requiredSkills);
        if (candidate.isEmpty()) {
            return new SkillFit(0.0, List.of(), List.copyOf(required.values()));
        }

        List<String> have = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        required.forEach((key, name) -> (candidate.containsKey(key) ? have : missing).add(name));

        String text = Objects.toString(jobTitle, "") + "\n" + Objects.toString(jobDescription, "");
        List<String> mentioned = candidate.entrySet().stream()
                .filter(e -> !required.containsKey(e.getKey()))
                .filter(e -> SkillText.mentions(text, e.getValue()) || SkillText.mentions(text, e.getKey()))
                .map(Map.Entry::getValue)
                .toList();

        double evidence = have.size() + MENTION_WEIGHT * mentioned.size();
        double score = Math.min(1.0, evidence / Math.max(required.size(), rules.minRequiredSkills())) * 100.0;
        // A skill in the title is the job's core ("Senior Software Engineer - .NET"): lacking it caps the score.
        boolean lacksTitleSkill = missing.stream().anyMatch(skill -> SkillText.mentions(Objects.toString(jobTitle, ""), skill));
        if (lacksTitleSkill && evidence > 0) {
            score = Math.min(score, rules.missingTitleSkillCap());
        }
        return new SkillFit(score, Stream.concat(have.stream(), mentioned.stream()).toList(), missing);
    }

    /** Lower-case name after aliases → the name as written, first spelling wins. */
    private Map<String, String> canonical(List<String> skills) {
        Map<String, String> byKey = new LinkedHashMap<>();
        if (skills != null) {
            skills.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(s -> byKey.putIfAbsent(alias(s.toLowerCase(Locale.ROOT), rules.skillAliases()), s));
        }
        return byKey;
    }

    /**
     * 100 within the job's range, in proportion below its minimum, and {@code matching.over-qualified-penalty-per-year}
     * less per year above its maximum (down to 20: an 18-year engineer for a new-grad job). Jobs are stored with
     * minimum 0 when nothing is known, so a range is known when the minimum is above 0 or a maximum is set.
     */
    Double experienceScore(Double candidateExperience, Integer minYears, Integer maxYears) {
        int min = minYears == null ? 0 : minYears;
        if (candidateExperience == null || candidateExperience <= 0 || (min <= 0 && maxYears == null)) {
            return null;
        }
        if (candidateExperience < min) {
            return Math.max(0.0, candidateExperience / min * 100);
        }
        if (maxYears != null && candidateExperience > maxYears) {
            return Math.max(20.0, 100.0 - (candidateExperience - maxYears) * rules.overQualifiedPenaltyPerYear());
        }
        return 100.0;
    }

    /** The best score over where the candidate lives and the places they are open to. */
    Double locationScore(String candidateLocation, List<String> preferredLocations, String jobLocation) {
        if (jobLocation == null || jobLocation.isBlank()) {
            return null;
        }
        return Stream.concat(Stream.ofNullable(candidateLocation),
                        preferredLocations == null ? Stream.empty() : preferredLocations.stream())
                .filter(l -> l != null && !l.isBlank())
                .map(l -> placeScore(l, jobLocation))
                .max(Double::compare)
                .orElse(null);
    }

    private double placeScore(String candidateLocation, String jobLocation) {
        String candidate = place(candidateLocation);
        String job = place(jobLocation);

        boolean remoteJob = job.contains("remote");
        if (candidate.contains("remote")) {
            // Open to remote work: a fit for remote jobs only, not for every onsite job
            return remoteJob ? 100.0 : 0.0;
        }
        // Same place, or same city ("Bengaluru" vs "Bengaluru, Karnataka")
        if (candidate.equals(job) || city(candidate).equals(city(job))) {
            return 100.0;
        }
        // Partial match (same state or country)
        if (candidate.contains(job) || job.contains(candidate)) {
            return 50.0;
        }
        // A remote job can be done from where the candidate lives
        if (remoteJob) {
            return 75.0;
        }
        return 0.0;
    }

    /** Lower case, each comma-separated part through the location aliases. */
    private String place(String location) {
        return String.join(", ", Stream.of(location.toLowerCase(Locale.ROOT).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> alias(s, rules.locationAliases()))
                .toList());
    }

    private static String city(String place) {
        int comma = place.indexOf(',');
        return comma < 0 ? place : place.substring(0, comma);
    }

    private static String alias(String key, Map<String, String> aliases) {
        String canonical = aliases.get(key);
        return canonical == null ? key : canonical.toLowerCase(Locale.ROOT);
    }
}
