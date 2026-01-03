package org.operaton.fitpub.util;

import com.garmin.fit.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Debugging test to investigate timestamp parsing issues in FIT and GPX files.
 * This test logs detailed information about raw timestamps and their conversions.
 */
@Slf4j
class TimestampDebuggingTest {

    private FitParser fitParser;
    private GpxParser gpxParser;
    private SpeedSmoother speedSmoother;

    @BeforeEach
    void setUp() {
        speedSmoother = new SpeedSmoother();
        fitParser = new FitParser(speedSmoother);
        gpxParser = new GpxParser(speedSmoother);
    }

    @Test
    @DisplayName("Debug FIT file timestamp parsing with detailed logging")
    void debugFitTimestampParsing() throws IOException {
        log.info("=== FIT FILE TIMESTAMP DEBUGGING ===");
        log.info("Current system time: {}", LocalDateTime.now());
        log.info("Current system timezone: {}", ZoneId.systemDefault());
        log.info("Current Unix timestamp: {}", System.currentTimeMillis() / 1000);
        log.info("");

        // Load FIT file
        String fitFileName = "/69287079d5e0a4532ba818ee.fit";
        InputStream inputStream = getClass().getResourceAsStream(fitFileName);
        assertNotNull(inputStream, "FIT file should exist");
        byte[] fileData = inputStream.readAllBytes();
        inputStream.close();

        // Parse with FIT SDK directly to inspect raw values
        Decode decode = new Decode();
        MesgBroadcaster broadcaster = new MesgBroadcaster(decode);

        final long FIT_EPOCH_OFFSET = 631065600L;

        // Capture session message
        broadcaster.addListener(new SessionMesgListener() {
            @Override
            public void onMesg(SessionMesg mesg) {
                log.info("--- SESSION MESSAGE ---");
                if (mesg.getStartTime() != null) {
                    DateTime startTime = mesg.getStartTime();
                    long rawTimestamp = startTime.getTimestamp();
                    long unixTimestamp = rawTimestamp + FIT_EPOCH_OFFSET;
                    Instant instant = Instant.ofEpochSecond(unixTimestamp);
                    LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
                    ZonedDateTime zdt = ZonedDateTime.ofInstant(instant, ZoneId.of("UTC"));

                    log.info("Raw FIT timestamp: {} seconds", rawTimestamp);
                    log.info("FIT epoch offset: {} seconds", FIT_EPOCH_OFFSET);
                    log.info("Unix timestamp (raw + offset): {} seconds", unixTimestamp);
                    log.info("Unix timestamp as instant: {}", instant);
                    log.info("As UTC ZonedDateTime: {}", zdt);
                    log.info("As LocalDateTime (system TZ): {}", ldt);
                    log.info("Expected if recent 2024: ~2024-11-27T15:49:09");
                    log.info("");

                    // Verify timestamp is reasonable (not in far future or past)
                    long currentUnixTime = System.currentTimeMillis() / 1000;
                    long diffSeconds = Math.abs(currentUnixTime - unixTimestamp);
                    long diffDays = diffSeconds / (24 * 60 * 60);
                    log.info("Difference from current time: {} days", diffDays);

                    // Reasonable range: within 5 years
                    long maxDiffDays = 5 * 365;
                    assertTrue(diffDays < maxDiffDays,
                        "Timestamp should be within 5 years of current time. Diff: " + diffDays + " days");
                }

                if (mesg.getTimestamp() != null) {
                    DateTime timestamp = mesg.getTimestamp();
                    long rawTimestamp = timestamp.getTimestamp();
                    long unixTimestamp = rawTimestamp + FIT_EPOCH_OFFSET;
                    Instant instant = Instant.ofEpochSecond(unixTimestamp);

                    log.info("Session timestamp (FIT): {} seconds", rawTimestamp);
                    log.info("Session timestamp (Unix): {} seconds", unixTimestamp);
                    log.info("Session timestamp (UTC): {}", ZonedDateTime.ofInstant(instant, ZoneId.of("UTC")));
                }
            }
        });

        // Capture first record message
        final boolean[] firstRecordCaptured = {false};
        broadcaster.addListener(new RecordMesgListener() {
            @Override
            public void onMesg(RecordMesg mesg) {
                if (!firstRecordCaptured[0] && mesg.getTimestamp() != null) {
                    firstRecordCaptured[0] = true;
                    log.info("--- FIRST RECORD MESSAGE ---");
                    DateTime timestamp = mesg.getTimestamp();
                    long rawTimestamp = timestamp.getTimestamp();
                    long unixTimestamp = rawTimestamp + FIT_EPOCH_OFFSET;
                    Instant instant = Instant.ofEpochSecond(unixTimestamp);

                    log.info("First record raw timestamp: {} seconds", rawTimestamp);
                    log.info("First record Unix timestamp: {} seconds", unixTimestamp);
                    log.info("First record as UTC: {}", ZonedDateTime.ofInstant(instant, ZoneId.of("UTC")));
                    log.info("First record as LocalDateTime: {}",
                        LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));

                    if (mesg.getPositionLat() != null && mesg.getPositionLong() != null) {
                        double lat = mesg.getPositionLat() * (180.0 / Math.pow(2, 31));
                        double lon = mesg.getPositionLong() * (180.0 / Math.pow(2, 31));
                        log.info("First record position: lat={}, lon={}", lat, lon);
                    }
                }
            }
        });

        // Decode the file
        boolean success = decode.read(new ByteArrayInputStream(fileData), broadcaster);
        assertTrue(success, "FIT file should decode successfully");

        log.info("");
        log.info("=== PARSING WITH FitParser ===");
        ParsedActivityData parsedData = fitParser.parse(fileData);
        log.info("Parsed start time: {}", parsedData.getStartTime());
        log.info("Parsed end time: {}", parsedData.getEndTime());
        log.info("Activity type: {}", parsedData.getActivityType());
        log.info("Track points: {}", parsedData.getTrackPoints().size());

        if (!parsedData.getTrackPoints().isEmpty()) {
            ParsedActivityData.TrackPointData firstPoint = parsedData.getTrackPoints().get(0);
            log.info("First track point timestamp: {}", firstPoint.getTimestamp());
        }
    }

    @Test
    @DisplayName("Debug GPX file timestamp parsing with detailed logging")
    void debugGpxTimestampParsing() throws IOException {
        log.info("=== GPX FILE TIMESTAMP DEBUGGING ===");
        log.info("Current system time: {}", LocalDateTime.now());
        log.info("Current system timezone: {}", ZoneId.systemDefault());
        log.info("");

        // Load GPX file
        String gpxFileName = "/7410863774.gpx";
        InputStream inputStream = getClass().getResourceAsStream(gpxFileName);
        assertNotNull(inputStream, "GPX file should exist");
        byte[] fileData = inputStream.readAllBytes();
        inputStream.close();

        // Parse GPX file
        ParsedActivityData parsedData = gpxParser.parse(fileData);

        log.info("--- PARSED GPX DATA ---");
        log.info("Parsed start time: {}", parsedData.getStartTime());
        log.info("Parsed end time: {}", parsedData.getEndTime());
        log.info("Activity type: {}", parsedData.getActivityType());
        log.info("Timezone: {}", parsedData.getTimezone());
        log.info("Track points: {}", parsedData.getTrackPoints().size());

        if (!parsedData.getTrackPoints().isEmpty()) {
            ParsedActivityData.TrackPointData firstPoint = parsedData.getTrackPoints().get(0);
            log.info("First track point timestamp: {}", firstPoint.getTimestamp());
            log.info("First track point lat/lon: {}, {}", firstPoint.getLatitude(), firstPoint.getLongitude());

            // Check if timestamp is reasonable
            if (firstPoint.getTimestamp() != null) {
                long currentUnixTime = System.currentTimeMillis() / 1000;
                long pointUnixTime = firstPoint.getTimestamp().atZone(ZoneId.systemDefault()).toEpochSecond();
                long diffSeconds = Math.abs(currentUnixTime - pointUnixTime);
                long diffDays = diffSeconds / (24 * 60 * 60);
                log.info("Difference from current time: {} days", diffDays);

                // Verify within reasonable range
                long maxDiffDays = 10 * 365; // 10 years
                assertTrue(diffDays < maxDiffDays,
                    "Timestamp should be within 10 years of current time. Diff: " + diffDays + " days");
            }
        }

        // Extract and log raw XML timestamp from file
        String xmlContent = new String(fileData);
        int timeIdx = xmlContent.indexOf("<time>");
        if (timeIdx > 0) {
            int endIdx = xmlContent.indexOf("</time>", timeIdx);
            if (endIdx > 0) {
                String rawTimeString = xmlContent.substring(timeIdx + 6, endIdx);
                log.info("");
                log.info("Raw XML timestamp string: {}", rawTimeString);
                log.info("This should be in ISO-8601 format (YYYY-MM-DDTHH:MM:SSZ)");
            }
        }
    }

    @Test
    @DisplayName("Verify FIT epoch offset calculation")
    void verifyFitEpochOffset() {
        log.info("=== FIT EPOCH OFFSET VERIFICATION ===");

        // FIT epoch: 1989-12-31T00:00:00Z
        // Unix epoch: 1970-01-01T00:00:00Z

        LocalDateTime fitEpoch = LocalDateTime.of(1989, 12, 31, 0, 0, 0);
        LocalDateTime unixEpoch = LocalDateTime.of(1970, 1, 1, 0, 0, 0);

        ZonedDateTime fitEpochUtc = fitEpoch.atZone(ZoneId.of("UTC"));
        ZonedDateTime unixEpochUtc = unixEpoch.atZone(ZoneId.of("UTC"));

        long fitEpochSeconds = fitEpochUtc.toEpochSecond();
        long unixEpochSeconds = unixEpochUtc.toEpochSecond();

        long calculatedOffset = fitEpochSeconds - unixEpochSeconds;
        final long EXPECTED_OFFSET = 631065600L;

        log.info("Unix epoch: {}", unixEpochUtc);
        log.info("Unix epoch seconds: {}", unixEpochSeconds);
        log.info("FIT epoch: {}", fitEpochUtc);
        log.info("FIT epoch seconds: {}", fitEpochSeconds);
        log.info("Calculated offset: {} seconds", calculatedOffset);
        log.info("Expected offset: {} seconds", EXPECTED_OFFSET);
        log.info("Offset in days: {} days", calculatedOffset / (24 * 60 * 60));
        log.info("Offset in years: {} years (approx)", calculatedOffset / (24 * 60 * 60 * 365.25));

        assertEquals(EXPECTED_OFFSET, calculatedOffset,
            "Calculated FIT epoch offset should match expected value");
    }

    @Test
    @DisplayName("Test manual timestamp conversion examples")
    void testManualTimestampConversions() {
        log.info("=== MANUAL TIMESTAMP CONVERSION EXAMPLES ===");

        final long FIT_EPOCH_OFFSET = 631065600L;

        // Example: Convert a known date to see what FIT timestamp it should have
        // Let's say we know the activity should be from 2024-11-27T15:49:09 UTC
        LocalDateTime expectedDate = LocalDateTime.of(2024, 11, 27, 15, 49, 9);
        ZonedDateTime expectedUtc = expectedDate.atZone(ZoneId.of("UTC"));
        long expectedUnixTimestamp = expectedUtc.toEpochSecond();
        long expectedFitTimestamp = expectedUnixTimestamp - FIT_EPOCH_OFFSET;

        log.info("Expected date: {}", expectedDate);
        log.info("Expected Unix timestamp: {}", expectedUnixTimestamp);
        log.info("Expected FIT timestamp (Unix - offset): {}", expectedFitTimestamp);
        log.info("");

        // Now reverse: what date do we get if FIT timestamp is X?
        // This simulates what the parser does
        long simulatedFitTimestamp = expectedFitTimestamp;
        long calculatedUnixTimestamp = simulatedFitTimestamp + FIT_EPOCH_OFFSET;
        Instant instant = Instant.ofEpochSecond(calculatedUnixTimestamp);
        LocalDateTime calculatedDate = LocalDateTime.ofInstant(instant, ZoneId.of("UTC"));

        log.info("Simulated FIT timestamp: {}", simulatedFitTimestamp);
        log.info("Calculated Unix timestamp (FIT + offset): {}", calculatedUnixTimestamp);
        log.info("Calculated date: {}", calculatedDate);
        log.info("");

        // They should match
        assertEquals(expectedDate, calculatedDate,
            "Round-trip conversion should produce the same date");
        log.info("Round-trip conversion: PASSED ✓");
    }
}
