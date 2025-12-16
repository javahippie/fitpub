package org.operaton.fitpub.util;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.operaton.fitpub.model.entity.Activity;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for FitParser using a real FIT file.
 */
@Slf4j
class FitParserIntegrationTest {

    private FitParser parser;
    private FitFileValidator validator;
    private SpeedSmoother speedSmoother;

    @BeforeEach
    void setUp() {
        speedSmoother = new SpeedSmoother();
        parser = new FitParser(speedSmoother);
        validator = new FitFileValidator();
    }

    @Test
    @DisplayName("Should successfully parse real FIT file from test resources")
    void testParseRealFitFile() throws IOException {
        // Load the real FIT file from test resources
        String fitFileName = "/69287079d5e0a4532ba818ee.fit";
        InputStream inputStream = getClass().getResourceAsStream(fitFileName);

        assertNotNull(inputStream, "FIT file should exist in test resources: " + fitFileName);

        // Read file into byte array
        byte[] fileData = inputStream.readAllBytes();
        inputStream.close();

        // Validate the file
        assertDoesNotThrow(() -> validator.validate(fileData),
            "Real FIT file should pass validation");

        // Parse the file
        FitParser.ParsedFitData parsedData = assertDoesNotThrow(
            () -> parser.parse(fileData),
            "Real FIT file should parse without errors"
        );

        // Verify parsed data structure
        assertNotNull(parsedData, "Parsed data should not be null");

        // Verify track points
        assertNotNull(parsedData.getTrackPoints(), "Track points should not be null");
        assertFalse(parsedData.getTrackPoints().isEmpty(), "Track points should not be empty");

        log.info("Successfully parsed real FIT file:");
        log.info("   Track points: {}", parsedData.getTrackPoints().size());
        log.info("   Activity type: {}", parsedData.getActivityType());

        if (parsedData.getStartTime() != null) {
            log.info("   Start time: {}", parsedData.getStartTime());
        }

        if (parsedData.getEndTime() != null) {
            log.info("   End time: {}", parsedData.getEndTime());
        }

        if (parsedData.getTotalDistance() != null) {
            log.info("   Total distance: {} meters", parsedData.getTotalDistance());
        }

        if (parsedData.getTotalDuration() != null) {
            long minutes = parsedData.getTotalDuration().toMinutes();
            long seconds = parsedData.getTotalDuration().getSeconds() % 60;
            log.info("   Total duration: {}m {}s", minutes, seconds);
        }

        if (parsedData.getElevationGain() != null) {
            log.info("   Elevation gain: {} meters", parsedData.getElevationGain());
        }

        if (parsedData.getElevationLoss() != null) {
            log.info("   Elevation loss: {} meters", parsedData.getElevationLoss());
        }

        // Verify at least some basic data
        assertNotNull(parsedData.getActivityType(), "Activity type should be determined");
        assertTrue(parsedData.getTrackPoints().size() > 0, "Should have at least one track point");

        // Verify track point data quality
        FitParser.TrackPointData firstPoint = parsedData.getTrackPoints().get(0);
        assertNotNull(firstPoint, "First track point should not be null");
        assertNotEquals(0.0, firstPoint.getLatitude(), "Latitude should be set");
        assertNotEquals(0.0, firstPoint.getLongitude(), "Longitude should be set");

        // Verify GPS coordinates are in valid range
        assertTrue(firstPoint.getLatitude() >= -90 && firstPoint.getLatitude() <= 90,
            "Latitude should be in valid range (-90 to 90)");
        assertTrue(firstPoint.getLongitude() >= -180 && firstPoint.getLongitude() <= 180,
            "Longitude should be in valid range (-180 to 180)");

        log.info("   First point: lat={}, lon={}", firstPoint.getLatitude(), firstPoint.getLongitude());

        if (firstPoint.getElevation() != null) {
            log.info("   First point elevation: {} meters", firstPoint.getElevation());
        }

        if (firstPoint.getHeartRate() != null) {
            log.info("   First point heart rate: {} bpm", firstPoint.getHeartRate());
        }

        // Verify metrics if present
        if (parsedData.getMetrics() != null) {
            FitParser.ActivityMetricsData metrics = parsedData.getMetrics();
            log.info("Metrics:");

            if (metrics.getAverageSpeed() != null) {
                log.info("   Average speed: {} km/h", metrics.getAverageSpeed());
            }

            if (metrics.getMaxSpeed() != null) {
                log.info("   Max speed: {} km/h", metrics.getMaxSpeed());
            }

            if (metrics.getAverageHeartRate() != null) {
                log.info("   Average heart rate: {} bpm", metrics.getAverageHeartRate());
            }

            if (metrics.getMaxHeartRate() != null) {
                log.info("   Max heart rate: {} bpm", metrics.getMaxHeartRate());
            }

            if (metrics.getCalories() != null) {
                log.info("   Calories: {}", metrics.getCalories());
            }

            if (metrics.getAverageCadence() != null) {
                log.info("   Average cadence: {}", metrics.getAverageCadence());
            }

            if (metrics.getAveragePower() != null) {
                log.info("   Average power: {} watts", metrics.getAveragePower());
            }
        }
    }

    @Test
    @DisplayName("Should extract complete activity data from real FIT file")
    void testExtractCompleteActivityData() throws IOException {
        // Load the real FIT file
        String fitFileName = "/69287079d5e0a4532ba818ee.fit";
        InputStream inputStream = getClass().getResourceAsStream(fitFileName);
        byte[] fileData = inputStream.readAllBytes();
        inputStream.close();

        // Parse the file
        FitParser.ParsedFitData parsedData = parser.parse(fileData);

        // Test converting to entity structures
        Activity.ActivityType activityType = parsedData.getActivityType();
        assertNotNull(activityType, "Activity type should be extracted");

        // Verify we can convert track points to entities
        if (!parsedData.getTrackPoints().isEmpty()) {
            FitParser.TrackPointData trackPointData = parsedData.getTrackPoints().get(0);

            // Test geometry creation
            assertDoesNotThrow(() -> trackPointData.toGeometry(),
                "Should be able to create Point geometry from track point");

            var point = trackPointData.toGeometry();
            assertNotNull(point, "Point geometry should not be null");
            assertEquals(trackPointData.getLongitude(), point.getX(), 0.0001,
                "Point X coordinate should match longitude");
            assertEquals(trackPointData.getLatitude(), point.getY(), 0.0001,
                "Point Y coordinate should match latitude");
        }
    }

    @Test
    @DisplayName("Should validate real FIT file successfully")
    void testValidateRealFitFile() throws IOException {
        // Load the real FIT file
        String fitFileName = "/69287079d5e0a4532ba818ee.fit";
        InputStream inputStream = getClass().getResourceAsStream(fitFileName);
        byte[] fileData = inputStream.readAllBytes();
        inputStream.close();

        // Should pass all validation checks
        assertDoesNotThrow(() -> validator.validate(fileData),
            "Real FIT file should pass validation");

        // File should have valid extension
        assertTrue(validator.hasValidExtension(fitFileName),
            "File should have valid .fit extension");
    }

    @Test
    @DisplayName("Should handle track points in chronological order")
    void testTrackPointsChronologicalOrder() throws IOException {
        // Load the real FIT file
        String fitFileName = "/69287079d5e0a4532ba818ee.fit";
        InputStream inputStream = getClass().getResourceAsStream(fitFileName);
        byte[] fileData = inputStream.readAllBytes();
        inputStream.close();

        FitParser.ParsedFitData parsedData = parser.parse(fileData);

        // Verify track points are in chronological order
        if (parsedData.getTrackPoints().size() > 1) {
            for (int i = 0; i < parsedData.getTrackPoints().size() - 1; i++) {
                FitParser.TrackPointData current = parsedData.getTrackPoints().get(i);
                FitParser.TrackPointData next = parsedData.getTrackPoints().get(i + 1);

                if (current.getTimestamp() != null && next.getTimestamp() != null) {
                    assertTrue(
                        !current.getTimestamp().isAfter(next.getTimestamp()),
                        "Track points should be in chronological order at index " + i
                    );
                }
            }

            log.info("Track points are in chronological order");
            log.info("   First timestamp: {}", parsedData.getTrackPoints().get(0).getTimestamp());
            log.info("   Last timestamp: {}",
                parsedData.getTrackPoints().get(parsedData.getTrackPoints().size() - 1).getTimestamp());
        }
    }
}
