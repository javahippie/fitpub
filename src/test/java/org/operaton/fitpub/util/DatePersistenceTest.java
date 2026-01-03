package org.operaton.fitpub.util;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.ActivityRepository;
import org.operaton.fitpub.repository.UserRepository;
import org.operaton.fitpub.service.ActivityFileService;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify that activity dates are correctly persisted to
 * and retrieved from the database.
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
@Transactional
class DatePersistenceTest {

    @Autowired
    private ActivityFileService activityFileService;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FitParser fitParser;

    @Autowired
    private GpxParser gpxParser;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Test
    @DisplayName("FIT file dates should persist correctly to database")
    void testFitFileDatePersistence() throws IOException {
        log.info("=== TESTING FIT FILE DATE PERSISTENCE ===");

        // Create test user
        User user = createTestUser();

        // Load FIT file
        String fitFileName = "/69287079d5e0a4532ba818ee.fit";
        byte[] fileData = loadTestFile(fitFileName);

        // Parse first to see what we expect
        ParsedActivityData parsedData = fitParser.parse(fileData);
        LocalDateTime expectedStartTime = parsedData.getStartTime();
        LocalDateTime expectedEndTime = parsedData.getEndTime();

        log.info("BEFORE DATABASE:");
        log.info("  Parsed start time: {}", expectedStartTime);
        log.info("  Parsed end time: {}", expectedEndTime);

        // Upload via service (which saves to DB)
        MockMultipartFile mockFile = new MockMultipartFile(
            "file",
            "test-activity.fit",
            "application/octet-stream",
            fileData
        );

        Activity savedActivity = activityFileService.processActivityFile(
            mockFile,
            user.getId(),
            "Test Activity",
            "Testing date persistence",
            Activity.Visibility.PUBLIC
        );

        assertNotNull(savedActivity);
        assertNotNull(savedActivity.getId());

        log.info("");
        log.info("AFTER SAVING TO DATABASE:");
        log.info("  Saved ID: {}", savedActivity.getId());
        log.info("  Saved start time: {}", savedActivity.getStartedAt());
        log.info("  Saved end time: {}", savedActivity.getEndedAt());
        log.info("  Saved timezone: {}", savedActivity.getTimezone());

        // Compare
        assertEquals(expectedStartTime, savedActivity.getStartedAt(),
            "Start time should match parsed value");
        assertEquals(expectedEndTime, savedActivity.getEndedAt(),
            "End time should match parsed value");

        // Flush to ensure write to DB
        activityRepository.flush();

        // Query back from database
        Activity queriedActivity = activityRepository.findById(savedActivity.getId())
            .orElseThrow(() -> new AssertionError("Activity should exist in database"));

        log.info("");
        log.info("AFTER QUERYING FROM DATABASE:");
        log.info("  Queried start time: {}", queriedActivity.getStartedAt());
        log.info("  Queried end time: {}", queriedActivity.getEndedAt());
        log.info("  Queried timezone: {}", queriedActivity.getTimezone());

        // Verify dates survived round-trip
        assertEquals(expectedStartTime, queriedActivity.getStartedAt(),
            "Queried start time should match original parsed value");
        assertEquals(expectedEndTime, queriedActivity.getEndedAt(),
            "Queried end time should match original parsed value");

        // Also verify it's recent (within last 2 months)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime twoMonthsAgo = now.minusMonths(2);

        assertTrue(queriedActivity.getStartedAt().isAfter(twoMonthsAgo),
            String.format("Activity should be recent. Expected after %s, got %s",
                twoMonthsAgo.format(FORMATTER),
                queriedActivity.getStartedAt().format(FORMATTER)));

        log.info("");
        log.info("✅ FIT file date persistence: PASSED");
        log.info("  Expected: {}", expectedStartTime);
        log.info("  Got:      {}", queriedActivity.getStartedAt());
    }

    @Test
    @DisplayName("GPX file dates should persist correctly to database")
    void testGpxFileDatePersistence() throws IOException {
        log.info("=== TESTING GPX FILE DATE PERSISTENCE ===");

        // Create test user
        User user = createTestUser();

        // Load GPX file
        String gpxFileName = "/7410863774.gpx";
        byte[] fileData = loadTestFile(gpxFileName);

        // Parse first to see what we expect
        ParsedActivityData parsedData = gpxParser.parse(fileData);
        LocalDateTime expectedStartTime = parsedData.getStartTime();
        LocalDateTime expectedEndTime = parsedData.getEndTime();

        log.info("BEFORE DATABASE:");
        log.info("  Parsed start time: {}", expectedStartTime);
        log.info("  Parsed end time: {}", expectedEndTime);
        log.info("  Parsed timezone: {}", parsedData.getTimezone());

        // Upload via service
        MockMultipartFile mockFile = new MockMultipartFile(
            "file",
            "test-activity.gpx",
            "application/gpx+xml",
            fileData
        );

        Activity savedActivity = activityFileService.processActivityFile(
            mockFile,
            user.getId(),
            "Test GPX Activity",
            "Testing GPX date persistence",
            Activity.Visibility.PUBLIC
        );

        assertNotNull(savedActivity);

        log.info("");
        log.info("AFTER SAVING TO DATABASE:");
        log.info("  Saved start time: {}", savedActivity.getStartedAt());
        log.info("  Saved end time: {}", savedActivity.getEndedAt());
        log.info("  Saved timezone: {}", savedActivity.getTimezone());

        // Compare
        assertEquals(expectedStartTime, savedActivity.getStartedAt(),
            "Start time should match parsed value");
        assertEquals(expectedEndTime, savedActivity.getEndedAt(),
            "End time should match parsed value");

        // Query back
        activityRepository.flush();
        Activity queriedActivity = activityRepository.findById(savedActivity.getId())
            .orElseThrow(() -> new AssertionError("Activity should exist"));

        log.info("");
        log.info("AFTER QUERYING FROM DATABASE:");
        log.info("  Queried start time: {}", queriedActivity.getStartedAt());
        log.info("  Queried end time: {}", queriedActivity.getEndedAt());

        // Verify round-trip
        assertEquals(expectedStartTime, queriedActivity.getStartedAt(),
            "Queried start time should match original");
        assertEquals(expectedEndTime, queriedActivity.getEndedAt(),
            "Queried end time should match original");

        // This GPX file is from 2022, verify that
        assertEquals(2022, queriedActivity.getStartedAt().getYear(),
            "GPX file is from 2022");

        log.info("");
        log.info("✅ GPX file date persistence: PASSED");
        log.info("  Expected: {}", expectedStartTime);
        log.info("  Got:      {}", queriedActivity.getStartedAt());
    }

    @Test
    @DisplayName("Query activities ordered by date should show correct chronological order")
    void testQueryActivitiesOrderedByDate() throws IOException {
        log.info("=== TESTING ACTIVITY ORDERING BY DATE ===");

        User user = createTestUser();

        // Upload FIT file (Nov 2025 - recent)
        byte[] fitData = loadTestFile("/69287079d5e0a4532ba818ee.fit");
        MockMultipartFile fitFile = new MockMultipartFile(
            "file", "recent.fit", "application/octet-stream", fitData
        );
        Activity fitActivity = activityFileService.processActivityFile(
            fitFile, user.getId(), "Recent FIT", null, Activity.Visibility.PUBLIC
        );

        // Upload GPX file (July 2022 - old)
        byte[] gpxData = loadTestFile("/7410863774.gpx");
        MockMultipartFile gpxFile = new MockMultipartFile(
            "file", "old.gpx", "application/gpx+xml", gpxData
        );
        Activity gpxActivity = activityFileService.processActivityFile(
            gpxFile, user.getId(), "Old GPX", null, Activity.Visibility.PUBLIC
        );

        log.info("");
        log.info("UPLOADED ACTIVITIES:");
        log.info("  FIT (recent): {} - {}", fitActivity.getTitle(), fitActivity.getStartedAt());
        log.info("  GPX (old):    {} - {}", gpxActivity.getTitle(), gpxActivity.getStartedAt());

        activityRepository.flush();

        // Query ordered by date DESC (newest first)
        List<Activity> activitiesNewestFirst = activityRepository
            .findByUserIdOrderByStartedAtDesc(user.getId());

        log.info("");
        log.info("QUERY RESULT (newest first):");
        for (int i = 0; i < activitiesNewestFirst.size(); i++) {
            Activity a = activitiesNewestFirst.get(i);
            log.info("  [{}] {} - {}", i, a.getTitle(), a.getStartedAt());
        }

        // Verify order
        assertEquals(2, activitiesNewestFirst.size(), "Should have 2 activities");

        Activity first = activitiesNewestFirst.get(0);
        Activity second = activitiesNewestFirst.get(1);

        // First should be the FIT file (Nov 2025)
        assertEquals("Recent FIT", first.getTitle(),
            "Newest activity should be the FIT file");
        assertEquals(2025, first.getStartedAt().getYear(),
            "Newest activity should be from 2025");

        // Second should be the GPX file (July 2022)
        assertEquals("Old GPX", second.getTitle(),
            "Older activity should be the GPX file");
        assertEquals(2022, second.getStartedAt().getYear(),
            "Older activity should be from 2022");

        // Verify chronological order
        assertTrue(first.getStartedAt().isAfter(second.getStartedAt()),
            String.format("First activity (%s) should be after second (%s)",
                first.getStartedAt(), second.getStartedAt()));

        log.info("");
        log.info("✅ Activity ordering: PASSED");
        log.info("  Newest: {} ({})", first.getTitle(), first.getStartedAt());
        log.info("  Oldest: {} ({})", second.getTitle(), second.getStartedAt());
    }

    private User createTestUser() {
        User user = User.builder()
            .id(UUID.randomUUID())
            .username("testuser_" + System.currentTimeMillis())
            .email("test_" + System.currentTimeMillis() + "@example.com")
            .passwordHash("dummy_hash")
            .displayName("Test User")
            .publicKey("-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtest\n-----END PUBLIC KEY-----")
            .privateKey("-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtest\n-----END PRIVATE KEY-----")
            .enabled(true)
            .build();
        return userRepository.save(user);
    }

    private byte[] loadTestFile(String resourcePath) throws IOException {
        InputStream inputStream = getClass().getResourceAsStream(resourcePath);
        assertNotNull(inputStream, "Test file should exist: " + resourcePath);
        byte[] data = inputStream.readAllBytes();
        inputStream.close();
        return data;
    }
}
