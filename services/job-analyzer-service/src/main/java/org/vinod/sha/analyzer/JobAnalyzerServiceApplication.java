package org.vinod.sha.analyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.vinod.sha.analyzer.config.JobSyncProperties;
import org.vinod.sha.analyzer.service.JobLevels;
import org.vinod.sha.analyzer.service.JobSkills;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({JobSyncProperties.class, JobSkills.class, JobLevels.class})
public class JobAnalyzerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobAnalyzerServiceApplication.class, args);
    }
}

