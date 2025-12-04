package org.operaton.fitpub.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.TrainingLoad;
import org.operaton.fitpub.repository.ActivityRepository;
import org.operaton.fitpub.repository.TrainingLoadRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TrainingLoadService.
 * Tests TSS calculations, rolling averages, and form status.
 */
@ExtendWith(MockitoExtension.class)
class TrainingLoadServiceTest {

    @Mock
    private TrainingLoadRepository trainingLoadRepository;

    @Mock
    private ActivityRepository activityRepository;

    @InjectMocks
    private TrainingLoadService trainingLoadService;

    private UUID userId;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testDate = LocalDate.of(2025, 12, 1);
    }

    @Test
    @DisplayName("Should calculate TSS for activity with distance and elevation")
    void testCalculateTrainingStressScore_WithDistanceAndElevation() {
        // Given
        Activity activity = createActivity(
                3600L,  // 1 hour
                BigDecimal.valueOf(10000),  // 10 km
                BigDecimal.valueOf(100)     // 100m elevation
        );

        when(trainingLoadRepository.findByUserIdAndDate(userId, testDate))
                .thenReturn(Optional.empty());
        when(activityRepository.findByUserIdAndStartedAtBetweenOrderByStartedAtDesc(
                eq(userId), any(), any()))
                .thenReturn(List.of(activity));
        when(trainingLoadRepository.findByUserIdAndDateBetweenOrderByDateDesc(
                eq(userId), any(), any()))
                .thenReturn(List.of());

        // When
        trainingLoadService.updateDailyTrainingLoad(userId, testDate);

        // Then
        ArgumentCaptor<TrainingLoad> captor = ArgumentCaptor.forClass(TrainingLoad.class);
        verify(trainingLoadRepository).save(captor.capture());
        TrainingLoad saved = captor.getValue();

        assertNotNull(saved.getTrainingStressScore());
        assertTrue(saved.getTrainingStressScore().doubleValue() > 0);
        assertEquals(1, saved.getActivityCount());
        assertEquals(3600L, saved.getTotalDurationSeconds());
    }

    @Test
    @DisplayName("Should calculate TSS as zero for activity with no duration")
    void testCalculateTrainingStressScore_ZeroDuration() {
        // Given
        Activity activity = createActivity(0L, BigDecimal.ZERO, BigDecimal.ZERO);

        when(trainingLoadRepository.findByUserIdAndDate(userId, testDate))
                .thenReturn(Optional.empty());
        when(activityRepository.findByUserIdAndStartedAtBetweenOrderByStartedAtDesc(
                eq(userId), any(), any()))
                .thenReturn(List.of(activity));
        when(trainingLoadRepository.findByUserIdAndDateBetweenOrderByDateDesc(
                eq(userId), any(), any()))
                .thenReturn(List.of());

        // When
        trainingLoadService.updateDailyTrainingLoad(userId, testDate);

        // Then
        ArgumentCaptor<TrainingLoad> captor = ArgumentCaptor.forClass(TrainingLoad.class);
        verify(trainingLoadRepository).save(captor.capture());
        TrainingLoad saved = captor.getValue();

        assertEquals(BigDecimal.ZERO, saved.getTrainingStressScore());
    }

    @Test
    @DisplayName("Should aggregate multiple activities in one day")
    void testUpdateDailyTrainingLoad_MultipleActivities() {
        // Given
        Activity activity1 = createActivity(1800L, BigDecimal.valueOf(5000), BigDecimal.valueOf(50));
        Activity activity2 = createActivity(1800L, BigDecimal.valueOf(5000), BigDecimal.valueOf(50));

        when(trainingLoadRepository.findByUserIdAndDate(userId, testDate))
                .thenReturn(Optional.empty());
        when(activityRepository.findByUserIdAndStartedAtBetweenOrderByStartedAtDesc(
                eq(userId), any(), any()))
                .thenReturn(Arrays.asList(activity1, activity2));
        when(trainingLoadRepository.findByUserIdAndDateBetweenOrderByDateDesc(
                eq(userId), any(), any()))
                .thenReturn(List.of());

        // When
        trainingLoadService.updateDailyTrainingLoad(userId, testDate);

        // Then
        ArgumentCaptor<TrainingLoad> captor = ArgumentCaptor.forClass(TrainingLoad.class);
        verify(trainingLoadRepository).save(captor.capture());
        TrainingLoad saved = captor.getValue();

        assertEquals(2, saved.getActivityCount());
        assertEquals(3600L, saved.getTotalDurationSeconds()); // 1800 + 1800
        assertEquals(BigDecimal.valueOf(10000), saved.getTotalDistanceMeters()); // 5000 + 5000
    }

    @Test
    @DisplayName("Should calculate ATL (7-day rolling average)")
    void testCalculateRollingAverages_ATL() {
        // Given
        LocalDate today = testDate;
        List<TrainingLoad> last7Days = createTrainingLoadHistory(
                userId, today.minusDays(6), today, BigDecimal.valueOf(100.0)
        );

        when(trainingLoadRepository.findByUserIdAndDate(userId, today))
                .thenReturn(Optional.empty());
        when(activityRepository.findByUserIdAndStartedAtBetweenOrderByStartedAtDesc(
                eq(userId), any(), any()))
                .thenReturn(List.of(createActivity(3600L, BigDecimal.valueOf(10000), BigDecimal.ZERO)));
        when(trainingLoadRepository.findByUserIdAndDateBetweenOrderByDateDesc(
                eq(userId), eq(today.minusDays(6)), eq(today)))
                .thenReturn(last7Days);
        when(trainingLoadRepository.findByUserIdAndDateBetweenOrderByDateDesc(
                eq(userId), eq(today.minusDays(27)), eq(today)))
                .thenReturn(List.of());

        // When
        trainingLoadService.updateDailyTrainingLoad(userId, today);

        // Then
        ArgumentCaptor<TrainingLoad> captor = ArgumentCaptor.forClass(TrainingLoad.class);
        verify(trainingLoadRepository).save(captor.capture());
        TrainingLoad saved = captor.getValue();

        assertNotNull(saved.getAcuteTrainingLoad());
        // ATL should be average of 7 days (7 * 100 = 700 / 7 = 100)
        assertEquals(BigDecimal.valueOf(100.0).setScale(2), saved.getAcuteTrainingLoad());
    }

    @Test
    @DisplayName("Should calculate CTL (28-day rolling average)")
    void testCalculateRollingAverages_CTL() {
        // Given
        LocalDate today = testDate;
        List<TrainingLoad> last28Days = createTrainingLoadHistory(
                userId, today.minusDays(27), today, BigDecimal.valueOf(50.0)
        );

        when(trainingLoadRepository.findByUserIdAndDate(userId, today))
                .thenReturn(Optional.empty());
        when(activityRepository.findByUserIdAndStartedAtBetweenOrderByStartedAtDesc(
                eq(userId), any(), any()))
                .thenReturn(List.of(createActivity(3600L, BigDecimal.valueOf(10000), BigDecimal.ZERO)));
        when(trainingLoadRepository.findByUserIdAndDateBetweenOrderByDateDesc(
                eq(userId), eq(today.minusDays(6)), eq(today)))
                .thenReturn(List.of());
        when(trainingLoadRepository.findByUserIdAndDateBetweenOrderByDateDesc(
                eq(userId), eq(today.minusDays(27)), eq(today)))
                .thenReturn(last28Days);

        // When
        trainingLoadService.updateDailyTrainingLoad(userId, today);

        // Then
        ArgumentCaptor<TrainingLoad> captor = ArgumentCaptor.forClass(TrainingLoad.class);
        verify(trainingLoadRepository).save(captor.capture());
        TrainingLoad saved = captor.getValue();

        assertNotNull(saved.getChronicTrainingLoad());
        // CTL should be average of 28 days (28 * 50 = 1400 / 28 = 50)
        assertEquals(BigDecimal.valueOf(50.0).setScale(2), saved.getChronicTrainingLoad());
    }

    @Test
    @DisplayName("Should calculate TSB (Training Stress Balance)")
    void testCalculateRollingAverages_TSB() {
        // Given
        LocalDate today = testDate;
        List<TrainingLoad> last7Days = createTrainingLoadHistory(
                userId, today.minusDays(6), today, BigDecimal.valueOf(80.0)
        );
        List<TrainingLoad> last28Days = createTrainingLoadHistory(
                userId, today.minusDays(27), today, BigDecimal.valueOf(100.0)
        );

        when(trainingLoadRepository.findByUserIdAndDate(userId, today))
                .thenReturn(Optional.empty());
        when(activityRepository.findByUserIdAndStartedAtBetweenOrderByStartedAtDesc(
                eq(userId), any(), any()))
                .thenReturn(List.of(createActivity(3600L, BigDecimal.valueOf(10000), BigDecimal.ZERO)));
        when(trainingLoadRepository.findByUserIdAndDateBetweenOrderByDateDesc(
                eq(userId), eq(today.minusDays(6)), eq(today)))
                .thenReturn(last7Days);
        when(trainingLoadRepository.findByUserIdAndDateBetweenOrderByDateDesc(
                eq(userId), eq(today.minusDays(27)), eq(today)))
                .thenReturn(last28Days);

        // When
        trainingLoadService.updateDailyTrainingLoad(userId, today);

        // Then
        ArgumentCaptor<TrainingLoad> captor = ArgumentCaptor.forClass(TrainingLoad.class);
        verify(trainingLoadRepository).save(captor.capture());
        TrainingLoad saved = captor.getValue();

        assertNotNull(saved.getTrainingStressBalance());
        // TSB = CTL - ATL = 100 - 80 = 20
        assertEquals(BigDecimal.valueOf(20.0).setScale(2), saved.getTrainingStressBalance());
    }

    @Test
    @DisplayName("Should update existing training load entry")
    void testUpdateDailyTrainingLoad_UpdateExisting() {
        // Given
        TrainingLoad existing = TrainingLoad.builder()
                .userId(userId)
                .date(testDate)
                .activityCount(1)
                .trainingStressScore(BigDecimal.valueOf(50.0))
                .build();

        Activity newActivity = createActivity(3600L, BigDecimal.valueOf(10000), BigDecimal.ZERO);

        when(trainingLoadRepository.findByUserIdAndDate(userId, testDate))
                .thenReturn(Optional.of(existing));
        when(activityRepository.findByUserIdAndStartedAtBetweenOrderByStartedAtDesc(
                eq(userId), any(), any()))
                .thenReturn(List.of(newActivity));
        when(trainingLoadRepository.findByUserIdAndDateBetweenOrderByDateDesc(
                eq(userId), any(), any()))
                .thenReturn(List.of());

        // When
        trainingLoadService.updateDailyTrainingLoad(userId, testDate);

        // Then
        verify(trainingLoadRepository).save(any(TrainingLoad.class));
        // Should update the existing entry, not create new one
    }

    @Test
    @DisplayName("Should get recent training load for specified days")
    void testGetRecentTrainingLoad() {
        // Given
        int days = 30;
        LocalDate startDate = LocalDate.now().minusDays(days - 1);
        List<TrainingLoad> expectedLoad = List.of(
                createTrainingLoad(userId, testDate, BigDecimal.valueOf(100.0))
        );

        when(trainingLoadRepository.findByUserIdSinceDate(userId, startDate))
                .thenReturn(expectedLoad);

        // When
        List<TrainingLoad> result = trainingLoadService.getRecentTrainingLoad(userId, days);

        // Then
        assertEquals(expectedLoad, result);
        verify(trainingLoadRepository).findByUserIdSinceDate(userId, startDate);
    }

    @Test
    @DisplayName("Should get current form status")
    void testGetCurrentFormStatus() {
        // Given
        TrainingLoad latestLoad = TrainingLoad.builder()
                .userId(userId)
                .date(testDate)
                .acuteTrainingLoad(BigDecimal.valueOf(50.0))
                .chronicTrainingLoad(BigDecimal.valueOf(80.0))
                .trainingStressBalance(BigDecimal.valueOf(30.0))
                .build();

        when(trainingLoadRepository.findFirstByUserIdOrderByDateDesc(userId))
                .thenReturn(Optional.of(latestLoad));

        // When
        TrainingLoad.FormStatus status = trainingLoadService.getCurrentFormStatus(userId);

        // Then
        assertNotNull(status);
        verify(trainingLoadRepository).findFirstByUserIdOrderByDateDesc(userId);
    }

    @Test
    @DisplayName("Should return UNKNOWN when no training load exists")
    void testGetCurrentFormStatus_NoData() {
        // Given
        when(trainingLoadRepository.findFirstByUserIdOrderByDateDesc(userId))
                .thenReturn(Optional.empty());

        // When
        TrainingLoad.FormStatus status = trainingLoadService.getCurrentFormStatus(userId);

        // Then
        assertEquals(TrainingLoad.FormStatus.UNKNOWN, status);
    }

    // Helper methods

    private Activity createActivity(Long durationSeconds, BigDecimal distance, BigDecimal elevation) {
        return Activity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .activityType(Activity.ActivityType.RUN)
                .startedAt(testDate.atTime(10, 0))
                .totalDurationSeconds(durationSeconds)
                .totalDistance(distance)
                .elevationGain(elevation)
                .build();
    }

    private TrainingLoad createTrainingLoad(UUID userId, LocalDate date, BigDecimal tss) {
        return TrainingLoad.builder()
                .userId(userId)
                .date(date)
                .trainingStressScore(tss)
                .build();
    }

    private List<TrainingLoad> createTrainingLoadHistory(UUID userId, LocalDate start, LocalDate end, BigDecimal tss) {
        List<TrainingLoad> history = new java.util.ArrayList<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            history.add(createTrainingLoad(userId, current, tss));
            current = current.plusDays(1);
        }
        return history;
    }
}
