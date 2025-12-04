package org.operaton.fitpub.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.operaton.fitpub.model.entity.Achievement;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.ActivityMetrics;
import org.operaton.fitpub.repository.AchievementRepository;
import org.operaton.fitpub.repository.ActivityRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AchievementService.
 * Tests badge awarding logic for various achievement types.
 */
@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @Mock
    private AchievementRepository achievementRepository;

    @Mock
    private ActivityRepository activityRepository;

    @InjectMocks
    private AchievementService achievementService;

    private UUID userId;
    private LocalDateTime testTime;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testTime = LocalDateTime.of(2025, 12, 1, 10, 0);
    }

    @Test
    @DisplayName("Should award first activity achievement")
    void testCheckAndAwardAchievements_FirstActivity() {
        // Given
        Activity activity = createActivity(Activity.ActivityType.RUN, 5000L, BigDecimal.ZERO);

        when(activityRepository.countByUserId(userId)).thenReturn(1L);
        when(activityRepository.countByUserIdAndActivityType(userId, Activity.ActivityType.RUN)).thenReturn(1L);
        when(activityRepository.sumDistanceByUserId(userId)).thenReturn(BigDecimal.valueOf(5000));
        when(activityRepository.countDistinctActivityTypesByUserId(userId)).thenReturn(1L);
        when(activityRepository.existsByUserIdAndDate(any(), any())).thenReturn(true); // Has activity today for streak
        lenient().when(achievementRepository.existsByUserIdAndAchievementType(any(), any())).thenReturn(false);
        when(achievementRepository.save(any(Achievement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<Achievement> achievements = achievementService.checkAndAwardAchievements(activity);

        // Then
        assertFalse(achievements.isEmpty());
        assertTrue(achievements.stream().anyMatch(a ->
            a.getAchievementType() == Achievement.AchievementType.FIRST_ACTIVITY
        ));
        assertTrue(achievements.stream().anyMatch(a ->
            a.getAchievementType() == Achievement.AchievementType.FIRST_RUN
        ));
        verify(achievementRepository, atLeast(2)).save(any(Achievement.class));
    }

    @Test
    @DisplayName("Should award first run achievement")
    void testCheckAndAwardAchievements_FirstRun() {
        // Given
        Activity activity = createActivity(Activity.ActivityType.RUN, 5000L, BigDecimal.ZERO);

        when(activityRepository.countByUserId(userId)).thenReturn(10L); // Not first overall
        when(activityRepository.countByUserIdAndActivityType(userId, Activity.ActivityType.RUN)).thenReturn(1L);
        when(activityRepository.sumDistanceByUserId(userId)).thenReturn(BigDecimal.valueOf(50000));
        when(activityRepository.countDistinctActivityTypesByUserId(userId)).thenReturn(2L);
        when(activityRepository.existsByUserIdAndDate(any(), any())).thenReturn(false);
        when(achievementRepository.existsByUserIdAndAchievementType(any(), any())).thenReturn(false);
        when(achievementRepository.save(any(Achievement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<Achievement> achievements = achievementService.checkAndAwardAchievements(activity);

        // Then
        assertTrue(achievements.stream().anyMatch(a ->
            a.getAchievementType() == Achievement.AchievementType.FIRST_RUN
        ));
    }

    @Test
    @DisplayName("Should award distance milestone achievements")
    void testCheckAndAwardAchievements_DistanceMilestone() {
        // Given - User has completed 10+ km total
        Activity activity = createActivity(Activity.ActivityType.RUN, 5000L, BigDecimal.ZERO);

        when(activityRepository.countByUserId(userId)).thenReturn(5L);
        when(activityRepository.countByUserIdAndActivityType(any(), any())).thenReturn(3L);
        when(activityRepository.sumDistanceByUserId(userId)).thenReturn(BigDecimal.valueOf(12000)); // 12 km
        when(activityRepository.countDistinctActivityTypesByUserId(userId)).thenReturn(1L);
        when(activityRepository.existsByUserIdAndDate(any(), any())).thenReturn(false);
        when(achievementRepository.existsByUserIdAndAchievementType(any(), any())).thenReturn(false);
        when(achievementRepository.save(any(Achievement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<Achievement> achievements = achievementService.checkAndAwardAchievements(activity);

        // Then
        assertTrue(achievements.stream().anyMatch(a ->
            a.getAchievementType() == Achievement.AchievementType.DISTANCE_10K
        ));
        assertFalse(achievements.stream().anyMatch(a ->
            a.getAchievementType() == Achievement.AchievementType.DISTANCE_50K
        ));
    }

    @Test
    @DisplayName("Should award activity count milestone")
    void testCheckAndAwardAchievements_ActivityCount() {
        // Given - User has 10 activities
        Activity activity = createActivity(Activity.ActivityType.RUN, 5000L, BigDecimal.ZERO);

        when(activityRepository.countByUserId(userId)).thenReturn(10L);
        when(activityRepository.countByUserIdAndActivityType(any(), any())).thenReturn(5L);
        when(activityRepository.sumDistanceByUserId(userId)).thenReturn(BigDecimal.valueOf(50000));
        when(activityRepository.countDistinctActivityTypesByUserId(userId)).thenReturn(1L);
        when(activityRepository.existsByUserIdAndDate(any(), any())).thenReturn(false);
        when(achievementRepository.existsByUserIdAndAchievementType(any(), any())).thenReturn(false);
        when(achievementRepository.save(any(Achievement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<Achievement> achievements = achievementService.checkAndAwardAchievements(activity);

        // Then
        assertTrue(achievements.stream().anyMatch(a ->
            a.getAchievementType() == Achievement.AchievementType.ACTIVITIES_10
        ));
    }

    @Test
    @DisplayName("Should award early bird achievement")
    void testCheckAndAwardAchievements_EarlyBird() {
        // Given - Activity before 6am, and user has 5+ early activities
        Activity activity = createActivity(Activity.ActivityType.RUN, 5000L, BigDecimal.ZERO);
        activity.setStartedAt(LocalDateTime.of(2025, 12, 1, 5, 30)); // 5:30 AM

        when(activityRepository.countByUserId(userId)).thenReturn(10L);
        when(activityRepository.countByUserIdAndActivityType(any(), any())).thenReturn(5L);
        when(activityRepository.sumDistanceByUserId(userId)).thenReturn(BigDecimal.valueOf(50000));
        when(activityRepository.countDistinctActivityTypesByUserId(userId)).thenReturn(1L);
        when(activityRepository.existsByUserIdAndDate(any(), any())).thenReturn(false);
        when(activityRepository.countByUserIdAndStartTimeBefore(eq(userId), eq(LocalTime.of(6, 0)))).thenReturn(5L);
        when(achievementRepository.existsByUserIdAndAchievementType(any(), any())).thenReturn(false);
        when(achievementRepository.save(any(Achievement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<Achievement> achievements = achievementService.checkAndAwardAchievements(activity);

        // Then
        assertTrue(achievements.stream().anyMatch(a ->
            a.getAchievementType() == Achievement.AchievementType.EARLY_BIRD
        ));
    }

    @Test
    @DisplayName("Should award night owl achievement")
    void testCheckAndAwardAchievements_NightOwl() {
        // Given - Activity after 10pm, and user has 5+ late activities
        Activity activity = createActivity(Activity.ActivityType.RUN, 5000L, BigDecimal.ZERO);
        activity.setStartedAt(LocalDateTime.of(2025, 12, 1, 23, 0)); // 11:00 PM

        when(activityRepository.countByUserId(userId)).thenReturn(10L);
        when(activityRepository.countByUserIdAndActivityType(any(), any())).thenReturn(5L);
        when(activityRepository.sumDistanceByUserId(userId)).thenReturn(BigDecimal.valueOf(50000));
        when(activityRepository.countDistinctActivityTypesByUserId(userId)).thenReturn(1L);
        when(activityRepository.existsByUserIdAndDate(any(), any())).thenReturn(false);
        when(activityRepository.countByUserIdAndStartTimeAfter(eq(userId), eq(LocalTime.of(22, 0)))).thenReturn(5L);
        when(achievementRepository.existsByUserIdAndAchievementType(any(), any())).thenReturn(false);
        when(achievementRepository.save(any(Achievement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<Achievement> achievements = achievementService.checkAndAwardAchievements(activity);

        // Then
        assertTrue(achievements.stream().anyMatch(a ->
            a.getAchievementType() == Achievement.AchievementType.NIGHT_OWL
        ));
    }

    @Test
    @DisplayName("Should award mountaineer achievement for 1000m elevation")
    void testCheckAndAwardAchievements_Mountaineer1000m() {
        // Given - Activity with 1000m+ elevation gain
        Activity activity = createActivity(Activity.ActivityType.HIKE, 10000L, BigDecimal.valueOf(1200));

        when(activityRepository.countByUserId(userId)).thenReturn(5L);
        when(activityRepository.countByUserIdAndActivityType(any(), any())).thenReturn(3L);
        when(activityRepository.sumDistanceByUserId(userId)).thenReturn(BigDecimal.valueOf(50000));
        when(activityRepository.sumElevationGainByUserId(userId)).thenReturn(BigDecimal.valueOf(1200));
        when(activityRepository.countDistinctActivityTypesByUserId(userId)).thenReturn(1L);
        when(activityRepository.existsByUserIdAndDate(any(), any())).thenReturn(false);
        when(achievementRepository.existsByUserIdAndAchievementType(any(), any())).thenReturn(false);
        when(achievementRepository.save(any(Achievement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<Achievement> achievements = achievementService.checkAndAwardAchievements(activity);

        // Then
        assertTrue(achievements.stream().anyMatch(a ->
            a.getAchievementType() == Achievement.AchievementType.MOUNTAINEER_1000M
        ));
    }

    @Test
    @DisplayName("Should award total elevation milestones")
    void testCheckAndAwardAchievements_TotalElevation() {
        // Given - User has 5000m+ total elevation
        Activity activity = createActivity(Activity.ActivityType.HIKE, 10000L, BigDecimal.valueOf(500));

        when(activityRepository.countByUserId(userId)).thenReturn(20L);
        when(activityRepository.countByUserIdAndActivityType(any(), any())).thenReturn(10L);
        when(activityRepository.sumDistanceByUserId(userId)).thenReturn(BigDecimal.valueOf(200000));
        when(activityRepository.sumElevationGainByUserId(userId)).thenReturn(BigDecimal.valueOf(6000)); // 6000m total
        when(activityRepository.countDistinctActivityTypesByUserId(userId)).thenReturn(2L);
        when(activityRepository.existsByUserIdAndDate(any(), any())).thenReturn(false);
        when(achievementRepository.existsByUserIdAndAchievementType(any(), any())).thenReturn(false);
        when(achievementRepository.save(any(Achievement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<Achievement> achievements = achievementService.checkAndAwardAchievements(activity);

        // Then
        assertTrue(achievements.stream().anyMatch(a ->
            a.getAchievementType() == Achievement.AchievementType.MOUNTAINEER_5000M
        ));
        assertFalse(achievements.stream().anyMatch(a ->
            a.getAchievementType() == Achievement.AchievementType.MOUNTAINEER_10000M
        ));
    }

    @Test
    @DisplayName("Should award variety seeker achievement")
    void testCheckAndAwardAchievements_VarietySeeker() {
        // Given - User has tried 3+ different activity types
        Activity activity = createActivity(Activity.ActivityType.SWIM, 2000L, BigDecimal.ZERO);

        when(activityRepository.countByUserId(userId)).thenReturn(15L);
        when(activityRepository.countByUserIdAndActivityType(any(), any())).thenReturn(5L);
        when(activityRepository.sumDistanceByUserId(userId)).thenReturn(BigDecimal.valueOf(30000));
        when(activityRepository.countDistinctActivityTypesByUserId(userId)).thenReturn(3L);
        when(activityRepository.existsByUserIdAndDate(any(), any())).thenReturn(false);
        when(achievementRepository.existsByUserIdAndAchievementType(any(), any())).thenReturn(false);
        when(achievementRepository.save(any(Achievement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<Achievement> achievements = achievementService.checkAndAwardAchievements(activity);

        // Then
        assertTrue(achievements.stream().anyMatch(a ->
            a.getAchievementType() == Achievement.AchievementType.VARIETY_SEEKER
        ));
    }

    @Test
    @DisplayName("Should award speed demon achievement")
    void testCheckAndAwardAchievements_SpeedDemon() {
        // Given - Activity with 50+ km/h speed (13.89+ m/s)
        Activity activity = createActivity(Activity.ActivityType.RIDE, 20000L, BigDecimal.ZERO);
        ActivityMetrics metrics = new ActivityMetrics();
        metrics.setMaxSpeed(BigDecimal.valueOf(15.0)); // 54 km/h
        activity.setMetrics(metrics);

        when(activityRepository.countByUserId(userId)).thenReturn(10L);
        when(activityRepository.countByUserIdAndActivityType(any(), any())).thenReturn(5L);
        when(activityRepository.sumDistanceByUserId(userId)).thenReturn(BigDecimal.valueOf(200000));
        when(activityRepository.countDistinctActivityTypesByUserId(userId)).thenReturn(1L);
        when(activityRepository.existsByUserIdAndDate(any(), any())).thenReturn(false);
        when(achievementRepository.existsByUserIdAndAchievementType(any(), any())).thenReturn(false);
        when(achievementRepository.save(any(Achievement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<Achievement> achievements = achievementService.checkAndAwardAchievements(activity);

        // Then
        assertTrue(achievements.stream().anyMatch(a ->
            a.getAchievementType() == Achievement.AchievementType.SPEED_DEMON
        ));
    }

    @Test
    @DisplayName("Should award 7-day streak achievement")
    void testCheckAndAwardAchievements_7DayStreak() {
        // Given - User has 7+ consecutive days of activities
        Activity activity = createActivity(Activity.ActivityType.RUN, 5000L, BigDecimal.ZERO);

        // Mock activity repository to return true for last 7 days
        when(activityRepository.countByUserId(userId)).thenReturn(20L);
        when(activityRepository.countByUserIdAndActivityType(any(), any())).thenReturn(10L);
        when(activityRepository.sumDistanceByUserId(userId)).thenReturn(BigDecimal.valueOf(100000));
        when(activityRepository.countDistinctActivityTypesByUserId(userId)).thenReturn(1L);
        when(activityRepository.existsByUserIdAndDate(any(), any())).thenReturn(true);
        when(achievementRepository.existsByUserIdAndAchievementType(any(), any())).thenReturn(false);
        when(achievementRepository.save(any(Achievement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<Achievement> achievements = achievementService.checkAndAwardAchievements(activity);

        // Then
        assertTrue(achievements.stream().anyMatch(a ->
            a.getAchievementType() == Achievement.AchievementType.STREAK_7_DAYS
        ));
    }

    @Test
    @DisplayName("Should NOT award achievements if already earned")
    void testCheckAndAwardAchievements_AlreadyEarned() {
        // Given - User already has these achievements
        Activity activity = createActivity(Activity.ActivityType.RUN, 5000L, BigDecimal.ZERO);

        when(activityRepository.countByUserId(userId)).thenReturn(10L);
        when(achievementRepository.existsByUserIdAndAchievementType(any(), any())).thenReturn(true); // Already earned

        // When
        List<Achievement> achievements = achievementService.checkAndAwardAchievements(activity);

        // Then
        assertTrue(achievements.isEmpty());
        verify(achievementRepository, never()).save(any(Achievement.class));
    }

    @Test
    @DisplayName("Should NOT award achievements if userId is null")
    void testCheckAndAwardAchievements_NullUserId() {
        // Given
        Activity activity = createActivity(Activity.ActivityType.RUN, 5000L, BigDecimal.ZERO);
        activity.setUserId(null);

        // When
        List<Achievement> achievements = achievementService.checkAndAwardAchievements(activity);

        // Then
        assertTrue(achievements.isEmpty());
        verify(achievementRepository, never()).save(any(Achievement.class));
    }

    @Test
    @DisplayName("Should award multiple achievements in single activity")
    void testCheckAndAwardAchievements_Multiple() {
        // Given - Activity that triggers multiple achievements
        Activity activity = createActivity(Activity.ActivityType.RUN, 5000L, BigDecimal.valueOf(1100));
        ActivityMetrics metrics = new ActivityMetrics();
        metrics.setMaxSpeed(BigDecimal.valueOf(16.0)); // 57.6 km/h (unrealistic for run, but for testing)
        activity.setMetrics(metrics);

        when(activityRepository.countByUserId(userId)).thenReturn(1L); // First activity
        when(activityRepository.countByUserIdAndActivityType(userId, Activity.ActivityType.RUN)).thenReturn(1L);
        when(activityRepository.sumDistanceByUserId(userId)).thenReturn(BigDecimal.valueOf(5000));
        when(activityRepository.sumElevationGainByUserId(userId)).thenReturn(BigDecimal.valueOf(1100));
        when(activityRepository.countDistinctActivityTypesByUserId(userId)).thenReturn(1L);
        when(activityRepository.existsByUserIdAndDate(any(), any())).thenReturn(false);
        when(achievementRepository.existsByUserIdAndAchievementType(any(), any())).thenReturn(false);
        when(achievementRepository.save(any(Achievement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<Achievement> achievements = achievementService.checkAndAwardAchievements(activity);

        // Then
        assertFalse(achievements.isEmpty());
        assertTrue(achievements.size() >= 2); // Should have multiple achievements
        assertTrue(achievements.stream().anyMatch(a ->
            a.getAchievementType() == Achievement.AchievementType.FIRST_ACTIVITY
        ));
        assertTrue(achievements.stream().anyMatch(a ->
            a.getAchievementType() == Achievement.AchievementType.MOUNTAINEER_1000M
        ));
    }

    @Test
    @DisplayName("Should get user achievements")
    void testGetUserAchievements() {
        // Given
        List<Achievement> expectedAchievements = List.of(
            createAchievement(Achievement.AchievementType.FIRST_ACTIVITY),
            createAchievement(Achievement.AchievementType.FIRST_RUN)
        );

        when(achievementRepository.findByUserIdOrderByEarnedAtDesc(userId)).thenReturn(expectedAchievements);

        // When
        List<Achievement> achievements = achievementService.getUserAchievements(userId);

        // Then
        assertEquals(2, achievements.size());
        verify(achievementRepository).findByUserIdOrderByEarnedAtDesc(userId);
    }

    @Test
    @DisplayName("Should get achievement count")
    void testGetAchievementCount() {
        // Given
        when(achievementRepository.countByUserId(userId)).thenReturn(5L);

        // When
        long count = achievementService.getAchievementCount(userId);

        // Then
        assertEquals(5L, count);
        verify(achievementRepository).countByUserId(userId);
    }

    // Helper methods

    private Activity createActivity(Activity.ActivityType activityType, long distanceMeters, BigDecimal elevationGain) {
        return Activity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .activityType(activityType)
                .startedAt(testTime)
                .totalDistance(BigDecimal.valueOf(distanceMeters))
                .totalDurationSeconds(3600L)
                .elevationGain(elevationGain)
                .build();
    }

    private Achievement createAchievement(Achievement.AchievementType achievementType) {
        return Achievement.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .achievementType(achievementType)
                .name("Test Achievement")
                .description("Test Description")
                .badgeIcon("🎉")
                .badgeColor("#ff00ff")
                .earnedAt(testTime)
                .build();
    }
}
