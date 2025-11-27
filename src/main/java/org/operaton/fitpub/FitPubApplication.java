package org.operaton.fitpub;

import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.config.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

/**
 * Main Spring Boot application class for FitPub.
 * FitPub is a federated fitness tracking platform that integrates with the Fediverse
 * through the ActivityPub protocol.
 */
@SpringBootApplication
@EnableAsync
@Slf4j
@Import(TestcontainersConfiguration.class)
public class FitPubApplication {

    public static void main(String[] args) {
        SpringApplication.run(FitPubApplication.class, args);
        log.info("FitPub application started successfully!");
        log.info("Upload your FIT files and share your activities with the Fediverse!");
    }

    /**
     * REST template for making HTTP requests to remote ActivityPub servers.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
