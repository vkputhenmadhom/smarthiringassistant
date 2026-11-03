package org.vinod.shared.text;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Finds skill names in free text as whole words, so "trust" is not Rust and "JavaScript" is not Java.
 * Skill names that are also everyday words (Go, R, C) only count when written with their usual capitals.
 */
public final class SkillText {

    /** Letters, digits, '+' and '#' continue a word: "C" is not found in "C++" or "C#". */
    private static final String WORD_CHAR = "[\\p{L}\\p{N}+#]";

    private static final Map<String, String> EVERYDAY_WORDS = Map.of("go", "Go", "r", "R", "c", "C");

    private static final Map<String, Pattern> PATTERNS = new ConcurrentHashMap<>();

    private SkillText() {
    }

    /** True if {@code text} mentions {@code skill} as a whole word. */
    public static boolean mentions(String text, String skill) {
        if (text == null || skill == null || skill.isBlank()) {
            return false;
        }
        return PATTERNS.computeIfAbsent(skill.trim(), SkillText::compile).matcher(text).find();
    }

    /** The skills from {@code dictionary} that {@code text} mentions, in dictionary order. */
    public static List<String> find(String text, Collection<String> dictionary) {
        if (text == null || text.isBlank() || dictionary == null) {
            return List.of();
        }
        return dictionary.stream()
                .filter(Objects::nonNull)
                .filter(skill -> mentions(text, skill))
                .distinct()
                .toList();
    }

    private static Pattern compile(String skill) {
        String everydaySpelling = EVERYDAY_WORDS.get(skill.toLowerCase(Locale.ROOT));
        String word = everydaySpelling == null ? skill : everydaySpelling;
        String regex = "(?<!" + WORD_CHAR + ")" + Pattern.quote(word) + "(?!" + WORD_CHAR + ")";
        return everydaySpelling == null
                ? Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                : Pattern.compile(regex);
    }
}
