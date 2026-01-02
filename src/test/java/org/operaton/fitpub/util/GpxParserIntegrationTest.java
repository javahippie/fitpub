package org.operaton.fitpub.util;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.util.ParsedActivityData.TrackPointData;
import org.operaton.fitpub.util.ParsedActivityData.ActivityMetricsData;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for GpxParser using a real GPX file.
 * Tests parsing of GPX files exported from Strava with Garmin extensions.
 */
@Slf4j
class GpxParserIntegrationTest {

    private GpxParser parser;
    private GpxFileValidator validator;
    private SpeedSmoother speedSmoother;

    @BeforeEach
    void setUp() {
        speedSmoother = new SpeedSmoother();
        parser = new GpxParser(speedSmoother);
        validator = new GpxFileValidator();
    }

    @Test
    @DisplayName("Should successfully parse real GPX file from test resources")
    void testParseRealGpxFile() throws IOException {
        // Load the real GPX file from test resources
        String gpxFileName = "/7410863774.gpx";
        InputStream inputStream = getClass().getResourceAsStream(gpxFileName);

        assertNotNull(inputStream, "GPX file should exist in test resources: " + gpxFileName);

        // Read file into byte array
        byte[] fileData = inputStream.readAllBytes();
        inputStream.close();

        // Validate the file
        assertDoesNotThrow(() -> validator.validate(fileData),
            "Real GPX file should pass validation");

        // Parse the file
        ParsedActivityData parsedData = assertDoesNotThrow(
            () -> parser.parse(fileData),
            "Real GPX file should parse without errors"
        );

        // Verify parsed data structure
        assertNotNull(parsedData, "Parsed data should not be null");
        assertEquals("GPX", parsedData.getSourceFormat(), "Source format should be GPX");

        // Verify track points
        assertNotNull(parsedData.getTrackPoints(), "Track points should not be null");
        assertFalse(parsedData.getTrackPoints().isEmpty(), "Track points should not be empty");

        log.info("Successfully parsed real GPX file:");
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

        if (parsedData.getTimezone() != null) {
            log.info("   Timezone: {}", parsedData.getTimezone());
        }

        // Verify at least some basic data
        assertNotNull(parsedData.getActivityType(), "Activity type should be determined");
        assertEquals(Activity.ActivityType.RUN, parsedData.getActivityType(),
            "Activity type should be RUN (from GPX <type>running</type>)");
        assertTrue(parsedData.getTrackPoints().size() > 0, "Should have at least one track point");

        // Verify track point data quality
        TrackPointData firstPoint = parsedData.getTrackPoints().get(0);
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

        // Verify calculated metrics (GPX doesn't have session summaries, so we calculate them)
        if (parsedData.getMetrics() != null) {
            ActivityMetricsData metrics = parsedData.getMetrics();
            log.info("Calculated Metrics:");

            if (metrics.getAverageSpeed() != null) {
                log.info("   Average speed: {} km/h", metrics.getAverageSpeed());
                assertTrue(metrics.getAverageSpeed().compareTo(BigDecimal.ZERO) > 0,
                    "Average speed should be positive");
            }

            if (metrics.getMaxSpeed() != null) {
                log.info("   Max speed: {} km/h", metrics.getMaxSpeed());
                assertTrue(metrics.getMaxSpeed().compareTo(BigDecimal.ZERO) > 0,
                    "Max speed should be positive");
            }

            if (metrics.getAverageHeartRate() != null) {
                log.info("   Average heart rate: {} bpm", metrics.getAverageHeartRate());
                assertTrue(metrics.getAverageHeartRate() > 0 && metrics.getAverageHeartRate() < 220,
                    "Average heart rate should be in reasonable range");
            }

            if (metrics.getMaxHeartRate() != null) {
                log.info("   Max heart rate: {} bpm", metrics.getMaxHeartRate());
                assertTrue(metrics.getMaxHeartRate() > 0 && metrics.getMaxHeartRate() < 220,
                    "Max heart rate should be in reasonable range");
            }

            if (metrics.getMinElevation() != null) {
                log.info("   Min elevation: {} meters", metrics.getMinElevation());
            }

            if (metrics.getMaxElevation() != null) {
                log.info("   Max elevation: {} meters", metrics.getMaxElevation());
            }

            if (metrics.getMovingTime() != null) {
                log.info("   Moving time: {} seconds", metrics.getMovingTime().getSeconds());
            }

            if (metrics.getStoppedTime() != null) {
                log.info("   Stopped time: {} seconds", metrics.getStoppedTime().getSeconds());
            }
        }
    }

    @Test
    @DisplayName("Should extract heart rate data from Garmin extensions")
    void testExtractHeartRateFromExtensions() throws IOException {
        // Load the real GPX file
        String gpxFileName = "/7410863774.gpx";
        InputStream inputStream = getClass().getResourceAsStream(gpxFileName);
        byte[] fileData = inputStream.readAllBytes();
        inputStream.close();

        // Parse the file
        ParsedActivityData parsedData = parser.parse(fileData);

        // Verify heart rate data is extracted from extensions
        long pointsWithHeartRate = parsedData.getTrackPoints().stream()
            .filter(tp -> tp.getHeartRate() != null)
            .count();

        assertTrue(pointsWithHeartRate > 0,
            "Should have extracted heart rate data from Garmin TrackPointExtension");

        log.info("Points with heart rate data: {} out of {}",
            pointsWithHeartRate, parsedData.getTrackPoints().size());

        // Verify first point has heart rate
        TrackPointData firstPoint = parsedData.getTrackPoints().get(0);
        assertNotNull(firstPoint.getHeartRate(),
            "First point should have heart rate from extension");
        assertTrue(firstPoint.getHeartRate() > 0 && firstPoint.getHeartRate() < 220,
            "Heart rate should be in reasonable range (0-220 bpm)");

        log.info("First point heart rate: {} bpm", firstPoint.getHeartRate());
    }

    @Test
    @DisplayName("Should calculate distance from GPS coordinates using Haversine formula")
    void testCalculateDistanceFromCoordinates() throws IOException {
        // Load the real GPX file
        String gpxFileName = "/7410863774.gpx";
        InputStream inputStream = getClass().getResourceAsStream(gpxFileName);
        byte[] fileData = inputStream.readAllBytes();
        inputStream.close();

        ParsedActivityData parsedData = parser.parse(fileData);

        // Verify total distance was calculated
        assertNotNull(parsedData.getTotalDistance(), "Total distance should be calculated");
        assertTrue(parsedData.getTotalDistance().compareTo(BigDecimal.ZERO) > 0,
            "Total distance should be positive");

        log.info("Calculated total distance: {} meters", parsedData.getTotalDistance());

        // Verify distance is reasonable for a running activity
        // (GPX files don't have session summaries, so we calculate from track points)
        double distanceKm = parsedData.getTotalDistance().doubleValue() / 1000.0;
        assertTrue(distanceKm > 0 && distanceKm < 100,
            "Distance should be reasonable for a running activity");

        log.info("Distance in km: {}", distanceKm);
    }

    @Test
    @DisplayName("Should calculate elevation gain and loss from track points")
    void testCalculateElevationMetrics() throws IOException {
        // Load the real GPX file
        String gpxFileName = "/7410863774.gpx";
        InputStream inputStream = getClass().getResourceAsStream(gpxFileName);
        byte[] fileData = inputStream.readAllBytes();
        inputStream.close();

        ParsedActivityData parsedData = parser.parse(fileData);

        // Verify elevation metrics were calculated
        assertNotNull(parsedData.getElevationGain(), "Elevation gain should be calculated");
        assertNotNull(parsedData.getElevationLoss(), "Elevation loss should be calculated");

        // Elevation gain/loss should be non-negative
        assertTrue(parsedData.getElevationGain().compareTo(BigDecimal.ZERO) >= 0,
            "Elevation gain should be non-negative");
        assertTrue(parsedData.getElevationLoss().compareTo(BigDecimal.ZERO) >= 0,
            "Elevation loss should be non-negative");

        log.info("Calculated elevation gain: {} meters", parsedData.getElevationGain());
        log.info("Calculated elevation loss: {} meters", parsedData.getElevationLoss());

        // Verify min/max elevation in metrics
        if (parsedData.getMetrics() != null) {
            BigDecimal minElev = parsedData.getMetrics().getMinElevation();
            BigDecimal maxElev = parsedData.getMetrics().getMaxElevation();

            if (minElev != null && maxElev != null) {
                assertTrue(maxElev.compareTo(minElev) >= 0,
                    "Max elevation should be >= min elevation");
                log.info("Elevation range: {} - {} meters", minElev, maxElev);
            }
        }
    }

    @Test
    @DisplayName("Should validate real GPX file successfully")
    void testValidateRealGpxFile() throws IOException {
        // Load the real GPX file
        String gpxFileName = "/7410863774.gpx";
        InputStream inputStream = getClass().getResourceAsStream(gpxFileName);
        byte[] fileData = inputStream.readAllBytes();
        inputStream.close();

        // Should pass all validation checks
        assertDoesNotThrow(() -> validator.validate(fileData),
            "Real GPX file should pass validation");

        // File should have valid extension
        assertTrue(validator.hasValidExtension(gpxFileName),
            "File should have valid .gpx extension");
    }

    @Test
    @DisplayName("Should handle track points in chronological order")
    void testTrackPointsChronologicalOrder() throws IOException {
        // Load the real GPX file
        String gpxFileName = "/7410863774.gpx";
        InputStream inputStream = getClass().getResourceAsStream(gpxFileName);
        byte[] fileData = inputStream.readAllBytes();
        inputStream.close();

        ParsedActivityData parsedData = parser.parse(fileData);

        // Verify track points are in chronological order
        if (parsedData.getTrackPoints().size() > 1) {
            for (int i = 0; i < parsedData.getTrackPoints().size() - 1; i++) {
                TrackPointData current = parsedData.getTrackPoints().get(i);
                TrackPointData next = parsedData.getTrackPoints().get(i + 1);

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

    @Test
    @DisplayName("Should calculate speed from consecutive GPS points")
    void testSpeedCalculation() throws IOException {
        // Load the real GPX file
        String gpxFileName = "/7410863774.gpx";
        InputStream inputStream = getClass().getResourceAsStream(gpxFileName);
        byte[] fileData = inputStream.readAllBytes();
        inputStream.close();

        ParsedActivityData parsedData = parser.parse(fileData);

        // Verify speed was calculated for track points
        long pointsWithSpeed = parsedData.getTrackPoints().stream()
            .filter(tp -> tp.getSpeed() != null)
            .count();

        assertTrue(pointsWithSpeed > 0,
            "Should have calculated speed for track points");

        log.info("Points with calculated speed: {} out of {}",
            pointsWithSpeed, parsedData.getTrackPoints().size());

        // Verify speeds are reasonable for running
        for (TrackPointData point : parsedData.getTrackPoints()) {
            if (point.getSpeed() != null) {
                double speedKmh = point.getSpeed().doubleValue();
                assertTrue(speedKmh >= 0 && speedKmh < 50,
                    "Running speed should be reasonable (0-50 km/h)");
            }
        }
    }

    @Test
    @DisplayName("Should extract complete activity data from real GPX file")
    void testExtractCompleteActivityData() throws IOException {
        // Load the real GPX file
        String gpxFileName = "/7410863774.gpx";
        InputStream inputStream = getClass().getResourceAsStream(gpxFileName);
        byte[] fileData = inputStream.readAllBytes();
        inputStream.close();

        // Parse the file
        ParsedActivityData parsedData = parser.parse(fileData);

        // Test converting to entity structures
        Activity.ActivityType activityType = parsedData.getActivityType();
        assertNotNull(activityType, "Activity type should be extracted");
        assertEquals(Activity.ActivityType.RUN, activityType,
            "Activity should be detected as RUN from GPX <type>running</type>");

        // Verify we can convert track points to entities
        if (!parsedData.getTrackPoints().isEmpty()) {
            TrackPointData trackPointData = parsedData.getTrackPoints().get(0);

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

        // Verify timezone was determined from GPS coordinates
        assertNotNull(parsedData.getTimezone(), "Timezone should be determined from GPS");
        assertFalse(parsedData.getTimezone().isEmpty(), "Timezone should not be empty");
        log.info("Determined timezone: {}", parsedData.getTimezone());

        // Verify start and end times
        assertNotNull(parsedData.getStartTime(), "Start time should be set");
        assertNotNull(parsedData.getEndTime(), "End time should be set");
        assertTrue(!parsedData.getStartTime().isAfter(parsedData.getEndTime()),
            "Start time should be before or equal to end time");

        // Verify total duration
        assertNotNull(parsedData.getTotalDuration(), "Total duration should be calculated");
        assertTrue(parsedData.getTotalDuration().getSeconds() > 0,
            "Total duration should be positive");
    }

    @Test
    @DisplayName("Should apply speed smoothing to remove GPS artifacts")
    void testSpeedSmoothing() throws IOException {
        // Load the real GPX file
        String gpxFileName = "/7410863774.gpx";
        InputStream inputStream = getClass().getResourceAsStream(gpxFileName);
        byte[] fileData = inputStream.readAllBytes();
        inputStream.close();

        ParsedActivityData parsedData = parser.parse(fileData);

        // Verify speed smoothing was applied (max speed should be reasonable after smoothing)
        if (parsedData.getMetrics() != null && parsedData.getMetrics().getMaxSpeed() != null) {
            double maxSpeedKmh = parsedData.getMetrics().getMaxSpeed().doubleValue();

            // For running, max speed should be reasonable after smoothing (typically < 30 km/h)
            assertTrue(maxSpeedKmh > 0 && maxSpeedKmh < 30,
                "Max speed should be reasonable for running after smoothing: " + maxSpeedKmh);

            log.info("Max speed after smoothing: {} km/h", maxSpeedKmh);
        }
    }
}
