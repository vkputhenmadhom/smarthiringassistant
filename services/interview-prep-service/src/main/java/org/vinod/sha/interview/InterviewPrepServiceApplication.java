package org.vinod.sha.interview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication(scanBasePackages = "org.vinod")
@EnableMongoRepositories(basePackages = "org.vinod.interview.repository")
public class InterviewPrepServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewPrepServiceApplication.class, args);
    }
}

