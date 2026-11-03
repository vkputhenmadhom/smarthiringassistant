package org.vinod.sha.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiIntegrationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiIntegrationServiceApplication.class, args);
    }
}

