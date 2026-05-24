package net.javahippie.fitpub;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
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
@ConfigurationPropertiesScan
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
     *
     * <p>Configures explicit connect/socket/response timeouts. Without these, a slow or
     * unresponsive remote inbox can hang the calling thread indefinitely (HttpClient's
     * default is no timeout). Federation deliveries run on the request thread for some
     * outbound activities, so a hung remote would otherwise block the user's HTTP
     * response. Values chosen to be generous enough for healthy peers but bounded
     * enough that one bad peer can't stall a request beyond a few seconds.
     */
    @Bean
    public RestTemplate restTemplate() {
        // Connection-level timeouts: how long to wait for the TCP / TLS handshake
        // and how long to wait for data on an established socket.
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(5))
            .setSocketTimeout(Timeout.ofSeconds(10))
            .build();

        HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
            .setDefaultConnectionConfig(connectionConfig)
            .build();

        // Request-level timeouts: how long to wait for a connection from the pool,
        // how long to wait for the first response byte, and the overall connect cap.
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(5))
            .setConnectionRequestTimeout(Timeout.ofSeconds(2))
            .setResponseTimeout(Timeout.ofSeconds(10))
            .build();

        HttpClient httpClient = HttpClientBuilder.create()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .disableRedirectHandling() // Don't follow redirects (important for federation)
            .build();

        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

        return new RestTemplate(requestFactory);
    }
}
