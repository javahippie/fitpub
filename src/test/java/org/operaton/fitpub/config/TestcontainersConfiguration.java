package org.operaton.fitpub.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers configuration for tests.
 * Automatically starts a PostgreSQL container with PostGIS extension for integration tests.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * PostgreSQL container with PostGIS extension for tests.
     * @ServiceConnection automatically configures the datasource from this container.
     */
    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4")
                .asCompatibleSubstituteFor("postgres")
        )
            .withDatabaseName("fitpub")
            .withUsername("fitpub")
            .withPassword("fitpub")
            .withReuse(true);
    }
}
