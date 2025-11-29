package org.operaton.fitpub.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Development data initializer that creates a demo user for testing.
 * Only active when the 'dev' profile is enabled.
 */
@Configuration
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevDataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initDemoUser() {
        return args -> {
            // Check if demo user already exists
            if (userRepository.findByUsername("demo").isPresent()) {
                log.info("Demo user already exists, skipping initialization");
                return;
            }

            log.info("Creating demo user for development...");

            try {
                // Generate RSA key pair for ActivityPub
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                KeyPair keyPair = keyGen.generateKeyPair();

                String publicKey = "-----BEGIN PUBLIC KEY-----\n" +
                        Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()) +
                        "\n-----END PUBLIC KEY-----";

                String privateKey = "-----BEGIN PRIVATE KEY-----\n" +
                        Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()) +
                        "\n-----END PRIVATE KEY-----";

                // Create demo user
                User demoUser = User.builder()
                        .username("demo")
                        .email("demo@fitpub.local")
                        .passwordHash(passwordEncoder.encode("demo"))
                        .displayName("Demo User")
                        .bio("This is a demo account for testing FitPub features. Upload your FIT files and explore the federated fitness tracking platform!")
                        .publicKey(publicKey)
                        .privateKey(privateKey)
                        .build();

                userRepository.save(demoUser);

                log.info("=".repeat(80));
                log.info("Demo user created successfully!");
                log.info("Username: demo");
                log.info("Password: demo");
                log.info("Email: demo@fitpub.local");
                log.info("=".repeat(80));

            } catch (NoSuchAlgorithmException e) {
                log.error("Failed to generate RSA key pair for demo user", e);
            }
        };
    }
}
