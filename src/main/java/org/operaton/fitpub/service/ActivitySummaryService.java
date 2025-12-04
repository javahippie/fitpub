package org.operaton.fitpub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.ActivitySummary;
import org.operaton.fitpub.repository.ActivityRepository;
import org.operaton.fitpub.repository.ActivitySummaryRepository;
import org.operaton.fitpub.repository.AchievementRepository;
import org.operaton.fitpub.repository.PersonalRecordRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for generating and managing activity summaries (weekly/monthly/yearly).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivitySummaryService {

    private final ActivitySummaryRepository activitySummaryRepository;
    private final ActivityRepository activityRepository;
    private final PersonalRecordRepository personalRecordRepository;
    private final AchievementRepository achievementRepository;

    /**
     * Update summary for an activity's period.
     * Called after an activity is saved.
     */
    @Async
    @Transactional
    public void updateSummariesForActivity(Activity activity) {
        if (activity.getUserId() == null || activity.getStartedAt() == null) {
            return;
        }

        LocalDate activityDate = activity.getStartedAt().toLocalDate();
        UUID userId = activity.getUserId();

        // Update weekly summary
        updateWeeklySummary(userId, activityDate);

        // Update monthly summary
        updateMonthlySummary(userId, activityDate);

        // Update yearly summary
        updateYearlySummary(userId, activityDate);
    }

    /**
     * Update weekly summary.
     */
    @Transactional
    public void updateWeeklySummary(UUID userId, LocalDate date) {
        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        ActivitySummary summary = activitySummaryRepository
                .findByUserIdAndPeriodTypeAndPeriodStart(userId, ActivitySummary.PeriodType.WEEK, weekStart)
                .orElse(ActivitySummary.builder()
                        .userId(userId)
                        .periodType(ActivitySummary.PeriodType.WEEK)
                        .periodStart(weekStart)
                        .periodEnd(weekEnd)
                        .build());

        calculateAndUpdateSummary(summary, userId, weekStart.atStartOfDay(), weekEnd.plusDays(1).atStartOfDay());
        activitySummaryRepository.save(summary);
    }

    /**
     * Update monthly summary.
     */
    @Transactional
    public void updateMonthlySummary(UUID userId, LocalDate date) {
        LocalDate monthStart = date.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate monthEnd = date.with(TemporalAdjusters.lastDayOfMonth());

        ActivitySummary summary = activitySummaryRepository
                .findByUserIdAndPeriodTypeAndPeriodStart(userId, ActivitySummary.PeriodType.MONTH, monthStart)
                .orElse(ActivitySummary.builder()
                        .userId(userId)
                        .periodType(ActivitySummary.PeriodType.MONTH)
                        .periodStart(monthStart)
                        .periodEnd(monthEnd)
                        .build());

        calculateAndUpdateSummary(summary, userId, monthStart.atStartOfDay(), monthEnd.plusDays(1).atStartOfDay());
        activitySummaryRepository.save(summary);
    }

    /**
     * Update yearly summary.
     */
    @Transactional
    public void updateYearlySummary(UUID userId, LocalDate date) {
        LocalDate yearStart = date.with(TemporalAdjusters.firstDayOfYear());
        LocalDate yearEnd = date.with(TemporalAdjusters.lastDayOfYear());

        ActivitySummary summary = activitySummaryRepository
                .findByUserIdAndPeriodTypeAndPeriodStart(userId, ActivitySummary.PeriodType.YEAR, yearStart)
                .orElse(ActivitySummary.builder()
                        .userId(userId)
                        .periodType(ActivitySummary.PeriodType.YEAR)
                        .periodStart(yearStart)
                        .periodEnd(yearEnd)
                        .build());

        calculateAndUpdateSummary(summary, userId, yearStart.atStartOfDay(), yearEnd.plusDays(1).atStartOfDay());
        activitySummaryRepository.save(summary);
    }

    /**
     * Calculate and update summary statistics.
     */
    private void calculateAndUpdateSummary(ActivitySummary summary, UUID userId,
                                          LocalDateTime startDateTime, LocalDateTime endDateTime) {
        // Get activities in period
        List<Activity> activities = activityRepository
                .findByUserIdAndStartedAtBetweenOrderByStartedAtDesc(userId, startDateTime, endDateTime);

        // Calculate totals
        int activityCount = activities.size();
        long totalDuration = activities.stream()
                .mapToLong(a -> a.getTotalDurationSeconds() != null ? a.getTotalDurationSeconds() : 0)
                .sum();
        BigDecimal totalDistance = activities.stream()
                .map(a -> a.getTotalDistance() != null ? a.getTotalDistance() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalElevation = activities.stream()
                .map(a -> a.getElevationGain() != null ? a.getElevationGain() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate max speed
        BigDecimal maxSpeed = activities.stream()
                .filter(a -> a.getMetrics() != null && a.getMetrics().getMaxSpeed() != null)
                .map(a -> a.getMetrics().getMaxSpeed())
                .max(BigDecimal::compareTo)
                .orElse(null);

        // Calculate average speed
        BigDecimal avgSpeed = null;
        if (totalDuration > 0 && totalDistance.compareTo(BigDecimal.ZERO) > 0) {
            avgSpeed = totalDistance.divide(BigDecimal.valueOf(totalDuration), 2, RoundingMode.HALF_UP);
        }

        // Activity type breakdown
        Map<String, Integer> typeBreakdown = new HashMap<>();
        for (Activity activity : activities) {
            String type = activity.getActivityType().name();
            typeBreakdown.put(type, typeBreakdown.getOrDefault(type, 0) + 1);
        }

        // Count PRs and achievements in this period
        long prsSet = personalRecordRepository.countByUserIdAndDateRange(
                userId,
                startDateTime,
                endDateTime
        );
        long achievementsEarned = achievementRepository.countByUserIdAndDateRange(
                userId,
                startDateTime,
                endDateTime
        );

        // Update summary
        summary.setActivityCount(activityCount);
        summary.setTotalDurationSeconds(totalDuration);
        summary.setTotalDistanceMeters(totalDistance);
        summary.setTotalElevationGainMeters(totalElevation);
        summary.setMaxSpeedMps(maxSpeed);
        summary.setAvgSpeedMps(avgSpeed);
        summary.setActivityTypeBreakdown(typeBreakdown);
        summary.setPersonalRecordsSet((int) prsSet);
        summary.setAchievementsEarned((int) achievementsEarned);
    }

    /**
     * Get weekly summaries for a user.
     */
    @Transactional(readOnly = true)
    public List<ActivitySummary> getWeeklySummaries(UUID userId, int weeks) {
        return activitySummaryRepository.findRecentByUserIdAndPeriodType(
                userId,
                ActivitySummary.PeriodType.WEEK,
                weeks
        );
    }

    /**
     * Get monthly summaries for a user.
     */
    @Transactional(readOnly = true)
    public List<ActivitySummary> getMonthlySummaries(UUID userId, int months) {
        return activitySummaryRepository.findRecentByUserIdAndPeriodType(
                userId,
                ActivitySummary.PeriodType.MONTH,
                months
        );
    }

    /**
     * Get yearly summaries for a user.
     */
    @Transactional(readOnly = true)
    public List<ActivitySummary> getYearlySummaries(UUID userId, int years) {
        return activitySummaryRepository.findRecentByUserIdAndPeriodType(
                userId,
                ActivitySummary.PeriodType.YEAR,
                years
        );
    }

    /**
     * Get current week summary.
     */
    @Transactional(readOnly = true)
    public ActivitySummary getCurrentWeekSummary(UUID userId) {
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        return activitySummaryRepository.findByUserIdAndPeriodTypeAndPeriodStart(
                userId,
                ActivitySummary.PeriodType.WEEK,
                weekStart
        ).orElse(null);
    }

    /**
     * Get current month summary.
     */
    @Transactional(readOnly = true)
    public ActivitySummary getCurrentMonthSummary(UUID userId) {
        LocalDate monthStart = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
        return activitySummaryRepository.findByUserIdAndPeriodTypeAndPeriodStart(
                userId,
                ActivitySummary.PeriodType.MONTH,
                monthStart
        ).orElse(null);
    }
}
