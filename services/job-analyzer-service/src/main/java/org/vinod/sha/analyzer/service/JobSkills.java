package org.vinod.sha.analyzer.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.vinod.shared.text.SkillText;

import java.util.List;

/**
 * The skills looked for in job titles and descriptions (job-skills.yml), each written as it is shown.
 *
 * @param dictionary skill names; found as whole words, case-insensitively
 */
@ConfigurationProperties("job-skills")
public record JobSkills(List<String> dictionary) {

    public JobSkills {
        dictionary = dictionary == null ? List.of() : List.copyOf(dictionary);
    }

    /** The dictionary's skills the text names, in dictionary order, as the dictionary writes them. */
    public List<String> find(String text) {
        return SkillText.find(text, dictionary);
    }

    public boolean contains(String skill) {
        return skill != null && dictionary.stream().anyMatch(d -> d.equalsIgnoreCase(skill.trim()));
    }
}
