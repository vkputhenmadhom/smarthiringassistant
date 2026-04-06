package org.vinod.sha.analyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.vinod.sha.analyzer.config.JobSyncProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(JobSyncProperties.class)
public class JobAnalyzerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobAnalyzerServiceApplication.class, args);
    }
}

