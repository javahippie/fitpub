package org.operaton.fitpub.config;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers configuration for development using Spring Boot Dev Services.
 * Automatically starts a PostgreSQL container with PostGIS extension when running in dev mode.
 *
 * This ensures development environment matches production (PostgreSQL + PostGIS).
 *
 * Only active when NOT running in production profile.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!prod")
public class TestcontainersConfiguration {

    /**
     * PostgreSQL container with PostGIS extension.
     * Uses postgis/postgis image which includes both PostgreSQL and PostGIS.
     *
     * @ServiceConnection automatically configures spring.datasource.* properties
     */
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4")
                .asCompatibleSubstituteFor("postgres")
        )
            .withDatabaseName("fitpub")
            .withUsername("fitpub")
            .withPassword("fitpub")
            .withReuse(true); // Reuse container across runs for faster startup
    }
}
