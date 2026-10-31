package org.vinod.sha.matcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Scoring rules from application.yaml ({@code matching.*}), so they can be tuned without code changes. A job's
 * seniority (level and years) comes from job-analyzer's job-skills.yml.
 *
 * @param minRequiredSkills           a job's skill share is divided by at least this many skills, so a job listing
 *                                    a single skill cannot score 100% on it alone
 * @param missingTitleSkillCap        the most the skill part can score when the job title names a skill the
 *                                    candidate lacks (".NET" in "Senior Software Engineer - .NET")
 * @param overQualifiedPenaltyPerYear points off the experience part per year above a job's range, down to 20
 * @param skillAliases                other spellings of a skill and the name to compare as ("reactjs" → "react")
 * @param locationAliases             other names of a place and the name to compare as ("bangalore" → "bengaluru")
 */
@ConfigurationProperties("matching")
public record MatchingRules(Integer minRequiredSkills, Integer missingTitleSkillCap, Integer overQualifiedPenaltyPerYear,
                            Map<String, String> skillAliases, Map<String, String> locationAliases) {

    public MatchingRules {
        minRequiredSkills = minRequiredSkills == null ? 3 : Math.max(1, minRequiredSkills);
        missingTitleSkillCap = missingTitleSkillCap == null ? 100 : Math.clamp(missingTitleSkillCap, 0, 100);
        overQualifiedPenaltyPerYear = overQualifiedPenaltyPerYear == null ? 8 : Math.max(0, overQualifiedPenaltyPerYear);
        skillAliases = skillAliases == null ? Map.of() : Map.copyOf(skillAliases);
        locationAliases = locationAliases == null ? Map.of() : Map.copyOf(locationAliases);
    }

    /** The defaults: divide by at least 3 skills, no title cap, 8 points a year over, no aliases. */
    public static MatchingRules defaults() {
        return new MatchingRules(null, null, null, null, null);
    }
}
