package org.vinod.sha.matcher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.vinod.sha.matcher.config.MatchingRules;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(MatchingRules.class)
public class CandidateMatcherServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CandidateMatcherServiceApplication.class, args);
    }
}

