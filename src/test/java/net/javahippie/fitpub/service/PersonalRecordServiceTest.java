package net.javahippie.fitpub.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import net.javahippie.fitpub.model.entity.Activity;
import net.javahippie.fitpub.model.entity.ActivityMetrics;
import net.javahippie.fitpub.model.entity.PersonalRecord;
import net.javahippie.fitpub.repository.PersonalRecordRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PersonalRecordService.
 * Tests PR detection and record-breaking logic.
 */
@ExtendWith(MockitoExtension.class)
class PersonalRecordServiceTest {

    @Mock
    private PersonalRecordRepository personalRecordRepository;

    @InjectMocks
    private PersonalRecordService personalRecordService;

    private UUID userId;
    private LocalDateTime testTime;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testTime = LocalDateTime.of(2025, 12, 1, 10, 0);
    }

    @Test
    @DisplayName("Should detect new longest distance PR")
    void testCheckPersonalRecords_NewLongestDistance() {
        // Given
        Activity activity = createActivity(
                10000L,  // 10 km
                3600L,   // 1 hour
                BigDecimal.valueOf(100)  // 100m elevation
        );

        when(personalRecordRepository.findByUserIdAndActivityTypeAndRecordType(
                any(), any(), any()))
                .thenReturn(Optional.empty());
        when(personalRecordRepository.save(any(PersonalRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<PersonalRecord> records = personalRecordService.checkAndUpdatePersonalRecords(activity);

        // Then
        assertFalse(records.isEmpty());
        assertTrue(records.stream().anyMatch(r ->
            r.getRecordType() == PersonalRecord.RecordType.LONGEST_DISTANCE
        ));
        verify(personalRecordRepository, atLeastOnce()).save(any(PersonalRecord.class));
    }

    @Test
    @DisplayName("Should detect improved longest distance PR")
    void testCheckPersonalRecords_ImprovedLongestDistance() {
        // Given
        Activity newActivity = createActivity(
                15000L,  // 15 km (new PR)
                4500L,   // 1.25 hours
                BigDecimal.valueOf(150)
        );

        PersonalRecord existingRecord = createPersonalRecord(
                PersonalRecord.RecordType.LONGEST_DISTANCE,
                BigDecimal.valueOf(10000),  // Old: 10 km
                "meters"
        );

        when(personalRecordRepository.findByUserIdAndActivityTypeAndRecordType(
                eq(userId), any(), eq(PersonalRecord.RecordType.LONGEST_DISTANCE)))
                .thenReturn(Optional.of(existingRecord));
        when(personalRecordRepository.save(any(PersonalRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<PersonalRecord> records = personalRecordService.checkAndUpdatePersonalRecords(newActivity);

        // Then
        assertFalse(records.isEmpty());
        PersonalRecord distanceRecord = records.stream()
                .filter(r -> r.getRecordType() == PersonalRecord.RecordType.LONGEST_DISTANCE)
                .findFirst()
                .orElse(null);

        assertNotNull(distanceRecord);
        assertEquals(BigDecimal.valueOf(15000), distanceRecord.getValue());
        assertEquals(BigDecimal.valueOf(10000), distanceRecord.getPreviousValue());
    }

    @Test
    @DisplayName("Should NOT detect PR when distance is lower than existing")
    void testCheckPersonalRecords_NoImprovementInDistance() {
        // Given
        Activity newActivity = createActivity(
                900L,    // 900m (< 1km, so no distance-based PRs)
                300L,    // 5 minutes duration
                BigDecimal.ZERO  // no elevation
        );

        PersonalRecord existingDistanceRecord = createPersonalRecord(
                PersonalRecord.RecordType.LONGEST_DISTANCE,
                BigDecimal.valueOf(10000),  // Existing: 10 km
                "meters"
        );

        PersonalRecord existingDurationRecord = createPersonalRecord(
                PersonalRecord.RecordType.LONGEST_DURATION,
                BigDecimal.valueOf(5000),  // Existing: longer duration
                "seconds"
        );

        // Return existing records that are better than current activity
        // For all PR types, return a record that's better than the new activity
        when(personalRecordRepository.findByUserIdAndActivityTypeAndRecordType(
                any(), any(), any()))
                .thenAnswer(invocation -> {
                    PersonalRecord.RecordType recordType = invocation.getArgument(2);
                    if (recordType == PersonalRecord.RecordType.LONGEST_DISTANCE) {
                        return Optional.of(existingDistanceRecord);
                    } else if (recordType == PersonalRecord.RecordType.LONGEST_DURATION) {
                        return Optional.of(existingDurationRecord);
                    } else if (recordType == PersonalRecord.RecordType.HIGHEST_ELEVATION_GAIN) {
                        // Better elevation gain than current (0m)
                        return Optional.of(createPersonalRecord(recordType, BigDecimal.valueOf(100), "meters"));
                    } else if (recordType == PersonalRecord.RecordType.BEST_AVERAGE_PACE) {
                        // Better (lower) pace than what the activity would produce
                        return Optional.of(createPersonalRecord(recordType, BigDecimal.valueOf(200), "seconds_per_km"));
                    } else if (recordType.name().startsWith("FASTEST_")) {
                        // Better (lower) time for all distance PRs
                        return Optional.of(createPersonalRecord(recordType, BigDecimal.valueOf(100), "seconds"));
                    }
                    return Optional.empty();
                });

        // When
        List<PersonalRecord> records = personalRecordService.checkAndUpdatePersonalRecords(newActivity);

        // Then
        assertTrue(records.stream().noneMatch(r ->
            r.getRecordType() == PersonalRecord.RecordType.LONGEST_DISTANCE
        ), "Should not have distance PR");
        assertTrue(records.stream().noneMatch(r ->
            r.getRecordType() == PersonalRecord.RecordType.LONGEST_DURATION
        ), "Should not have duration PR");
        verify(personalRecordRepository, never()).save(any(PersonalRecord.class));
    }

    @Test
    @DisplayName("Should detect fastest 5K PR")
    void testCheckPersonalRecords_Fastest5K() {
        // Given - Activity that covers at least 5km
        Activity activity = createActivity(
                6000L,   // 6 km
                1500L,   // 25 minutes
                BigDecimal.ZERO
        );

        when(personalRecordRepository.findByUserIdAndActivityTypeAndRecordType(
                any(), any(), any()))
                .thenReturn(Optional.empty());
        when(personalRecordRepository.save(any(PersonalRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<PersonalRecord> records = personalRecordService.checkAndUpdatePersonalRecords(activity);

        // Then
        assertTrue(records.stream().anyMatch(r ->
            r.getRecordType() == PersonalRecord.RecordType.FASTEST_5K
        ));
    }

    @Test
    @DisplayName("Should detect fastest 10K PR")
    void testCheckPersonalRecords_Fastest10K() {
        // Given - Activity that covers at least 10km
        Activity activity = createActivity(
                12000L,  // 12 km
                3000L,   // 50 minutes
                BigDecimal.ZERO
        );

        when(personalRecordRepository.findByUserIdAndActivityTypeAndRecordType(
                any(), any(), any()))
                .thenReturn(Optional.empty());
        when(personalRecordRepository.save(any(PersonalRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<PersonalRecord> records = personalRecordService.checkAndUpdatePersonalRecords(activity);

        // Then
        assertTrue(records.stream().anyMatch(r ->
            r.getRecordType() == PersonalRecord.RecordType.FASTEST_10K
        ));
    }

    @Test
    @DisplayName("Should detect half marathon PR")
    void testCheckPersonalRecords_FastestHalfMarathon() {
        // Given - Activity that covers at least 21.1km
        Activity activity = createActivity(
                21500L,  // 21.5 km
                6000L,   // 100 minutes
                BigDecimal.ZERO
        );

        when(personalRecordRepository.findByUserIdAndActivityTypeAndRecordType(
                any(), any(), any()))
                .thenReturn(Optional.empty());
        when(personalRecordRepository.save(any(PersonalRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<PersonalRecord> records = personalRecordService.checkAndUpdatePersonalRecords(activity);

        // Then
        assertTrue(records.stream().anyMatch(r ->
            r.getRecordType() == PersonalRecord.RecordType.FASTEST_HALF_MARATHON
        ));
    }

    @Test
    @DisplayName("Should detect marathon PR")
    void testCheckPersonalRecords_FastestMarathon() {
        // Given - Activity that covers at least 42.2km
        Activity activity = createActivity(
                42500L,  // 42.5 km
                12000L,  // 200 minutes
                BigDecimal.ZERO
        );

        when(personalRecordRepository.findByUserIdAndActivityTypeAndRecordType(
                any(), any(), any()))
                .thenReturn(Optional.empty());
        when(personalRecordRepository.save(any(PersonalRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<PersonalRecord> records = personalRecordService.checkAndUpdatePersonalRecords(activity);

        // Then
        assertTrue(records.stream().anyMatch(r ->
            r.getRecordType() == PersonalRecord.RecordType.FASTEST_MARATHON
        ));
    }

    @Test
    @DisplayName("Should detect highest elevation gain PR")
    void testCheckPersonalRecords_HighestElevationGain() {
        // Given
        Activity activity = createActivity(
                10000L,
                3600L,
                BigDecimal.valueOf(500)  // 500m elevation gain
        );

        when(personalRecordRepository.findByUserIdAndActivityTypeAndRecordType(
                any(), any(), any()))
                .thenReturn(Optional.empty());
        when(personalRecordRepository.save(any(PersonalRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<PersonalRecord> records = personalRecordService.checkAndUpdatePersonalRecords(activity);

        // Then
        assertTrue(records.stream().anyMatch(r ->
            r.getRecordType() == PersonalRecord.RecordType.HIGHEST_ELEVATION_GAIN
        ));
    }

    @Test
    @DisplayName("Should detect longest duration PR")
    void testCheckPersonalRecords_LongestDuration() {
        // Given
        Activity activity = createActivity(
                20000L,
                7200L,   // 2 hours
                BigDecimal.ZERO
        );

        when(personalRecordRepository.findByUserIdAndActivityTypeAndRecordType(
                any(), any(), any()))
                .thenReturn(Optional.empty());
        when(personalRecordRepository.save(any(PersonalRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<PersonalRecord> records = personalRecordService.checkAndUpdatePersonalRecords(activity);

        // Then
        assertTrue(records.stream().anyMatch(r ->
            r.getRecordType() == PersonalRecord.RecordType.LONGEST_DURATION
        ));
    }

    @Test
    @DisplayName("Should detect max speed PR from metrics")
    void testCheckPersonalRecords_MaxSpeed() {
        // Given
        Activity activity = createActivity(
                10000L,
                3600L,
                BigDecimal.ZERO
        );
        ActivityMetrics metrics = new ActivityMetrics();
        metrics.setMaxSpeed(BigDecimal.valueOf(5.5));  // 5.5 m/s
        activity.setMetrics(metrics);

        when(personalRecordRepository.findByUserIdAndActivityTypeAndRecordType(
                any(), any(), any()))
                .thenReturn(Optional.empty());
        when(personalRecordRepository.save(any(PersonalRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<PersonalRecord> records = personalRecordService.checkAndUpdatePersonalRecords(activity);

        // Then
        assertTrue(records.stream().anyMatch(r ->
            r.getRecordType() == PersonalRecord.RecordType.MAX_SPEED
        ));
    }

    @Test
    @DisplayName("Should detect best average pace PR")
    void testCheckPersonalRecords_BestAveragePace() {
        // Given - 10km in 3000 seconds = 5:00/km pace
        Activity activity = createActivity(
                10000L,  // 10 km
                3000L,   // 50 minutes
                BigDecimal.ZERO
        );

        when(personalRecordRepository.findByUserIdAndActivityTypeAndRecordType(
                any(), any(), any()))
                .thenReturn(Optional.empty());
        when(personalRecordRepository.save(any(PersonalRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<PersonalRecord> records = personalRecordService.checkAndUpdatePersonalRecords(activity);

        // Then
        assertTrue(records.stream().anyMatch(r ->
            r.getRecordType() == PersonalRecord.RecordType.BEST_AVERAGE_PACE
        ));
    }

    @Test
    @DisplayName("Should NOT create PRs for activity without userId")
    void testCheckPersonalRecords_NoUserId() {
        // Given
        Activity activity = createActivity(10000L, 3600L, BigDecimal.ZERO);
        activity.setUserId(null);

        // When
        List<PersonalRecord> records = personalRecordService.checkAndUpdatePersonalRecords(activity);

        // Then
        assertTrue(records.isEmpty());
        verify(personalRecordRepository, never()).save(any(PersonalRecord.class));
    }

    @Test
    @DisplayName("Should detect multiple PRs in single activity")
    void testCheckPersonalRecords_MultiplePRs() {
        // Given - Activity that sets multiple records
        Activity activity = createActivity(
                15000L,  // 15 km (distance PR)
                4500L,   // duration
                BigDecimal.valueOf(300)  // elevation PR
        );

        when(personalRecordRepository.findByUserIdAndActivityTypeAndRecordType(
                any(), any(), any()))
                .thenReturn(Optional.empty());
        when(personalRecordRepository.save(any(PersonalRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<PersonalRecord> records = personalRecordService.checkAndUpdatePersonalRecords(activity);

        // Then
        assertFalse(records.isEmpty());
        assertTrue(records.size() >= 2);  // Should have at least distance and elevation PRs
        assertTrue(records.stream().anyMatch(r ->
            r.getRecordType() == PersonalRecord.RecordType.LONGEST_DISTANCE
        ));
        assertTrue(records.stream().anyMatch(r ->
            r.getRecordType() == PersonalRecord.RecordType.HIGHEST_ELEVATION_GAIN
        ));
    }

    // Helper methods

    private Activity createActivity(long distanceMeters, long durationSeconds, BigDecimal elevationGain) {
        return Activity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .activityType(Activity.ActivityType.RUN)
                .startedAt(testTime)
                .totalDistance(BigDecimal.valueOf(distanceMeters))
                .totalDurationSeconds(durationSeconds)
                .elevationGain(elevationGain)
                .build();
    }

    private PersonalRecord createPersonalRecord(PersonalRecord.RecordType recordType,
                                               BigDecimal value, String unit) {
        return PersonalRecord.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .activityType(PersonalRecord.ActivityType.RUN)
                .recordType(recordType)
                .value(value)
                .unit(unit)
                .achievedAt(testTime.minusDays(30))  // Older record
                .build();
    }
}
