package net.javahippie.fitpub;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

/**
 * Main Spring Boot application class for FitPub.
 * FitPub is a federated fitness tracking platform that integrates with the Fediverse
 * through the ActivityPub protocol.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@Slf4j
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
        // Use Apache HttpClient with custom configuration
        HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
            .build();

        HttpClient httpClient = HttpClientBuilder.create()
            .setConnectionManager(connectionManager)
            .disableRedirectHandling() // Don't follow redirects (important for federation)
            .build();

        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

        return new RestTemplate(requestFactory);
    }
}
