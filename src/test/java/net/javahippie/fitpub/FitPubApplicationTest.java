package net.javahippie.fitpub;

import lombok.extern.slf4j.Slf4j;
import net.javahippie.fitpub.config.TestcontainersConfiguration;
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
public class FitPubApplicationTest {

    public static void main(String[] args) {
        SpringApplication
                .from(FitPubApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }

}
