package org.vinod.sha.analyzer.service;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.vinod.sha.analyzer.config.JobSyncProperties;

import java.io.IOException;
import java.io.UncheckedIOException;

/** The feeds exactly as the shipped job-feeds.yml configures them. */
public final class ConfiguredFeeds {

    private ConfiguredFeeds() {
    }

    /** The skill list exactly as the shipped job-skills.yml configures it. */
    public static JobSkills skills() {
        try {
            var yaml = new YamlPropertySourceLoader().load("job-skills", new ClassPathResource("job-skills.yml")).get(0);
            return new Binder(ConfigurationPropertySources.from(yaml)).bind("job-skills", JobSkills.class)
                    .orElseThrow(() -> new IllegalStateException("job-skills.yml has no job-skills section"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The seniority levels exactly as the shipped job-skills.yml configures them. */
    public static JobLevels levels() {
        try {
            var yaml = new YamlPropertySourceLoader().load("job-skills", new ClassPathResource("job-skills.yml")).get(0);
            return new Binder(ConfigurationPropertySources.from(yaml)).bind("job-levels", JobLevels.class)
                    .orElseThrow(() -> new IllegalStateException("job-skills.yml has no job-levels section"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static JobFeeds load() {
        try {
            var yaml = new YamlPropertySourceLoader().load("job-feeds", new ClassPathResource("job-feeds.yml")).get(0);
            JobSyncProperties props = new Binder(ConfigurationPropertySources.from(yaml))
                    .bind("job-sync", JobSyncProperties.class)
                    .orElseThrow(() -> new IllegalStateException("job-feeds.yml has no job-sync section"));
            return new JobFeeds(props);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
