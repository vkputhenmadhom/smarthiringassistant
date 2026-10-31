package org.vinod.sha.matcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Scoring rules from application.yaml ({@code matching.*}), so they can be tuned without code changes.
 *
 * @param minRequiredSkills a job's skill share is divided by at least this many skills, so a job listing a single
 *                          skill cannot score 100% on it alone
 * @param skillAliases      other spellings of a skill and the name to compare as ("reactjs" → "react")
 * @param locationAliases   other names of a place and the name to compare as ("bangalore" → "bengaluru")
 */
@ConfigurationProperties("matching")
public record MatchingRules(Integer minRequiredSkills, Map<String, String> skillAliases, Map<String, String> locationAliases) {

    public MatchingRules {
        minRequiredSkills = minRequiredSkills == null ? 3 : Math.max(1, minRequiredSkills);
        skillAliases = skillAliases == null ? Map.of() : Map.copyOf(skillAliases);
        locationAliases = locationAliases == null ? Map.of() : Map.copyOf(locationAliases);
    }

    /** The defaults: divide by at least 3 skills, no aliases. */
    public static MatchingRules defaults() {
        return new MatchingRules(null, null, null);
    }
}
