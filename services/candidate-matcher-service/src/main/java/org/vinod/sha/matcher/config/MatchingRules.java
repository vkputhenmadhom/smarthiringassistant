package org.vinod.sha.matcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Scoring rules from application.yaml ({@code matching.*}), so they can be tuned without code changes.
 *
 * @param minRequiredSkills       a job's skill share is divided by at least this many skills, so a job listing a
 *                                single skill cannot score 100% on it alone
 * @param missingTitleSkillCap    the most the skill part can score when the job title names a skill the
 *                                candidate lacks (".NET" in "Senior Software Engineer - .NET")
 * @param skillAliases            other spellings of a skill and the name to compare as ("reactjs" → "react")
 * @param locationAliases         other names of a place and the name to compare as ("bangalore" → "bengaluru")
 * @param seniority               levels read from job titles, for jobs that state no years of experience
 */
@ConfigurationProperties("matching")
public record MatchingRules(Integer minRequiredSkills, Integer missingTitleSkillCap,
                            Map<String, String> skillAliases, Map<String, String> locationAliases,
                            Seniority seniority) {

    public MatchingRules {
        minRequiredSkills = minRequiredSkills == null ? 3 : Math.max(1, minRequiredSkills);
        missingTitleSkillCap = missingTitleSkillCap == null ? 100 : Math.clamp(missingTitleSkillCap, 0, 100);
        skillAliases = skillAliases == null ? Map.of() : Map.copyOf(skillAliases);
        locationAliases = locationAliases == null ? Map.of() : Map.copyOf(locationAliases);
        seniority = seniority == null ? new Seniority(null, null) : seniority;
    }

    /** The defaults: divide by at least 3 skills, no caps, aliases or levels. */
    public static MatchingRules defaults() {
        return new MatchingRules(null, null, null, null, null);
    }

    /**
     * @param overQualifiedPenaltyPerYear points taken off per year above a level's range (down to 20)
     * @param levels                      the first level whose keyword the title contains (whole words) applies
     */
    public record Seniority(Integer overQualifiedPenaltyPerYear, List<Level> levels) {
        public Seniority {
            overQualifiedPenaltyPerYear = overQualifiedPenaltyPerYear == null ? 8 : Math.max(0, overQualifiedPenaltyPerYear);
            levels = levels == null ? List.of() : List.copyOf(levels);
        }
    }

    public record Level(String name, List<String> keywords, double minYears, double maxYears) {
        public Level {
            keywords = keywords == null ? List.of() : List.copyOf(keywords);
        }
    }
}
