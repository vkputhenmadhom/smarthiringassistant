package org.vinod.sha.analyzer.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.vinod.sha.analyzer.entity.JobAnalysis;
import org.vinod.shared.text.SkillText;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A job's seniority (job-skills.yml, {@code job-levels}): years the posting states win, otherwise the range of the
 * level its title names.
 */
@ConfigurationProperties("job-levels")
public record JobLevels(List<Level> levels) {

    /** @param maxYears null: no upper bound ("10+ yrs") */
    public record Level(String name, List<String> keywords, Integer minYears, Integer maxYears) {
        public Level {
            keywords = keywords == null ? List.of() : List.copyOf(keywords);
        }
    }

    /** @param level the title's level name, or null; years null when unknown */
    public record Seniority(String level, Integer minYears, Integer maxYears) {
        public static final Seniority UNKNOWN = new Seniority(null, null, null);
    }

    public JobLevels {
        levels = levels == null ? List.of() : List.copyOf(levels);
    }

    /** "5+ yrs", "8-12 years", "3 to 5 Yrs" in a title */
    private static final Pattern TITLE_YEARS = Pattern.compile(
            "(?i)\\b(\\d{1,2})\\s*(?:\\+|plus)?\\s*(?:(?:-|–|to)\\s*(\\d{1,2})\\s*\\+?\\s*)?(?:years?|yrs?)\\b");
    /** The same, followed by "experience" within a few words, in a description */
    private static final Pattern DESCRIPTION_YEARS = Pattern.compile(
            "(?i)\\b(\\d{1,2})\\s*(?:\\+|plus)?\\s*(?:(?:-|–|to)\\s*(\\d{1,2})\\s*\\+?\\s*)?(?:years?|yrs?)(?:\\s+[\\w-]+){0,4}?\\s+experience\\b");

    /** Sets the job's level and years from its title and description. */
    public JobAnalysis apply(JobAnalysis job) {
        Seniority s = of(job.getJobTitle(), job.getJobDescription());
        job.setSeniority(s.level());
        job.setMinExperienceYears(s.minYears());
        job.setMaxExperienceYears(s.maxYears());
        return job;
    }

    public Seniority of(String title, String description) {
        Optional<Level> level = levels.stream()
                .filter(l -> l.keywords().stream().anyMatch(k -> SkillText.mentions(title, k)))
                .findFirst();
        Optional<int[]> stated = years(TITLE_YEARS, title).or(() -> years(DESCRIPTION_YEARS, description));
        if (stated.isPresent()) {
            int[] y = stated.get();
            Integer max = y[1] > 0 ? Integer.valueOf(y[1]) : level.map(Level::maxYears).filter(m -> m >= y[0]).orElse(null);
            return new Seniority(level.map(Level::name).orElse(null), y[0], max);
        }
        return level.map(l -> new Seniority(l.name(), l.minYears(), l.maxYears())).orElse(Seniority.UNKNOWN);
    }

    /** [min, max or 0] from the first plausible match (at most 40 years) */
    private static Optional<int[]> years(Pattern pattern, String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            int min = Integer.parseInt(m.group(1));
            int max = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            if (min <= 40 && max <= 40 && (max == 0 || max >= min)) {
                return Optional.of(new int[]{min, max});
            }
        }
        return Optional.empty();
    }
}
