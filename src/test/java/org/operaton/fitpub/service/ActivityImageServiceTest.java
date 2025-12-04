package org.operaton.fitpub.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.ActivityMetrics;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.ActivityRepository;
import org.operaton.fitpub.repository.UserRepository;
import org.operaton.fitpub.util.FitParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Manual test for ActivityImageService.
 * These tests are disabled by default and should only be run manually.
 */
@SpringBootTest(properties = {
        "fitpub.image.osm-tiles.enabled=true"
})
@ActiveProfiles("test")
class ActivityImageServiceTest {

    @Autowired
    private ActivityImageService activityImageService;

    @Autowired
    private FitParser fitParser;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Manual test to generate an activity image from the test FIT file.
     * The image will be written to target/test-activity-image.png.
     *
     * To run this test manually:
     * mvn test -Dtest=ActivityImageServiceTest#testGenerateActivityImage_Manual
     */
    @Test
    @Disabled("Manual test - run explicitly when needed")
    @DisplayName("Generate activity image from test FIT file")
    void testGenerateActivityImage_Manual() throws Exception {
        // Load test FIT file
        Path fitFilePath = Paths.get("src/test/resources/69287079d5e0a4532ba818ee.fit");
        assertTrue(Files.exists(fitFilePath), "Test FIT file should exist");

        byte[] fitFileData = Files.readAllBytes(fitFilePath);
        assertNotNull(fitFileData);
        assertTrue(fitFileData.length > 0, "FIT file should not be empty");

        // Parse FIT file
        FitParser.ParsedFitData parsedData = fitParser.parse(fitFileData);
        assertNotNull(parsedData);
        assertNotNull(parsedData.getStartTime());
        assertNotNull(parsedData.getEndTime());
        assertFalse(parsedData.getTrackPoints().isEmpty(), "Should have track points");

        System.out.println("Parsed FIT file:");
        System.out.println("  Start time: " + parsedData.getStartTime());
        System.out.println("  End time: " + parsedData.getEndTime());
        System.out.println("  Timezone: " + parsedData.getTimezone());
        System.out.println("  Track points: " + parsedData.getTrackPoints().size());
        System.out.println("  Activity type: " + parsedData.getActivityType());
        System.out.println("  Total distance: " + parsedData.getTotalDistance() + " m");
        System.out.println("  Total duration: " + parsedData.getTotalDuration());

        // Create a test user with required fields
        User testUser = new User();
        testUser.setUsername("testuser_" + System.currentTimeMillis());
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash("hashedpassword");
        testUser.setDisplayName("Test User");
        testUser.setEnabled(true);
        // Dummy RSA keys for ActivityPub (not used in this test)
        testUser.setPublicKey("-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA\n-----END PUBLIC KEY-----");
        testUser.setPrivateKey("-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC\n-----END PRIVATE KEY-----");
        testUser = userRepository.save(testUser);

        // Create a test activity entity
        Activity activity = Activity.builder()
                .id(UUID.randomUUID())
                .userId(testUser.getId())
                .activityType(parsedData.getActivityType())
                .title("Test Activity - Manual Image Rendering")
                .description("This is a test activity for manual image rendering")
                .startedAt(parsedData.getStartTime())
                .endedAt(parsedData.getEndTime())
                .timezone(parsedData.getTimezone())
                .visibility(Activity.Visibility.PUBLIC)
                .totalDistance(parsedData.getTotalDistance())
                .totalDurationSeconds(parsedData.getTotalDuration() != null ? parsedData.getTotalDuration().getSeconds() : null)
                .elevationGain(parsedData.getElevationGain())
                .elevationLoss(parsedData.getElevationLoss())
                .build();

        // Add metrics if available
        if (parsedData.getMetrics() != null) {
            ActivityMetrics metrics = parsedData.getMetrics().toEntity(activity);
            activity.setMetrics(metrics);
        }

        // Convert track points to JSON
        String trackPointsJson = convertTrackPointsToJson(parsedData);
        activity.setTrackPointsJson(trackPointsJson);

        // Save activity temporarily (needed for image generation)
        Activity savedActivity = activityRepository.save(activity);

        try {
            System.out.println("\nGenerating activity image...");

            // Generate the image
            String imageUrl = activityImageService.generateActivityImage(savedActivity);

            System.out.println("Generated image URL: " + imageUrl);

            if (imageUrl == null) {
                System.err.println("ERROR: Image generation returned null! Check logs for errors.");
                fail("Image generation failed - returned null");
            }

            // Try multiple possible locations for the generated image
            String[] possiblePaths = {
                    "fitpub-images/" + savedActivity.getId() + ".png",
                    "/tmp/fitpub/images/" + savedActivity.getId() + ".png",
                    System.getProperty("java.io.tmpdir") + "/fitpub/images/" + savedActivity.getId() + ".png"
            };

            Path foundImagePath = null;
            for (String pathStr : possiblePaths) {
                Path testPath = Paths.get(pathStr);
                System.out.println("Checking: " + testPath.toAbsolutePath());
                if (Files.exists(testPath)) {
                    foundImagePath = testPath;
                    System.out.println("Found image at: " + testPath.toAbsolutePath());
                    break;
                }
            }

            if (foundImagePath != null) {
                // Copy the generated image to target directory for easy inspection
                Path targetPath = Paths.get("target", "test-activity-image.png");
                Files.copy(foundImagePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                System.out.println("\n✓ SUCCESS! Image copied to: " + targetPath.toAbsolutePath());
                System.out.println("Open this file to inspect the generated image with track overlay.");

                // Verify file size
                long fileSize = Files.size(targetPath);
                System.out.println("Image file size: " + fileSize + " bytes");
                assertTrue(fileSize > 1000, "Image file should be larger than 1KB");
            } else {
                System.err.println("ERROR: Generated image not found in any expected location!");
                fail("Generated image file not found");
            }

        } finally {
            // Clean up test activity and user
            activityRepository.delete(savedActivity);
            userRepository.delete(testUser);
            System.out.println("\nTest activity and user cleaned up.");
        }
    }

    /**
     * Helper method to convert parsed track points to JSON format.
     */
    private String convertTrackPointsToJson(FitParser.ParsedFitData parsedData) throws IOException {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        return mapper.writeValueAsString(parsedData.getTrackPoints());
    }
}
