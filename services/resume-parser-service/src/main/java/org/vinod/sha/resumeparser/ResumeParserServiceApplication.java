package org.vinod.sha.resumeparser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ResumeParserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResumeParserServiceApplication.class, args);
    }
}

