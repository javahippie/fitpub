package org.operaton.fitpub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.TrainingLoad;
import org.operaton.fitpub.repository.ActivityRepository;
import org.operaton.fitpub.repository.TrainingLoadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for calculating and managing training load metrics.
 * Implements Training Stress Score (TSS) and load balancing algorithms.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrainingLoadService {

    private final TrainingLoadRepository trainingLoadRepository;
    private final ActivityRepository activityRepository;

    /**
     * Update training load for an activity.
     * Called after an activity is saved.
     */
    @Transactional
    public void updateTrainingLoad(Activity activity) {
        if (activity.getUserId() == null || activity.getStartedAt() == null) {
            return;
        }

        LocalDate activityDate = activity.getStartedAt().toLocalDate();
        updateDailyTrainingLoad(activity.getUserId(), activityDate);
    }

    /**
     * Update daily training load for a specific date.
     */
    @Transactional
    public void updateDailyTrainingLoad(UUID userId, LocalDate date) {
        // Get or create training load entry
        TrainingLoad trainingLoad = trainingLoadRepository
                .findByUserIdAndDate(userId, date)
                .orElse(TrainingLoad.builder()
                        .userId(userId)
                        .date(date)
                        .build());

        // Get all activities for this date
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        List<Activity> activities = activityRepository
                .findByUserIdAndStartedAtBetweenOrderByStartedAtDesc(userId, startOfDay, endOfDay);

        // Calculate daily totals
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

        // Calculate Training Stress Score (simplified algorithm)
        BigDecimal tss = calculateTrainingStressScore(totalDuration, totalDistance, totalElevation);

        trainingLoad.setActivityCount(activityCount);
        trainingLoad.setTotalDurationSeconds(totalDuration);
        trainingLoad.setTotalDistanceMeters(totalDistance);
        trainingLoad.setTotalElevationGainMeters(totalElevation);
        trainingLoad.setTrainingStressScore(tss);

        // Calculate rolling averages
        calculateRollingAverages(trainingLoad, userId, date);

        trainingLoadRepository.save(trainingLoad);
        log.debug("Updated training load for user {} on {}: TSS={}", userId, date, tss);
    }

    /**
     * Calculate Training Stress Score (simplified).
     * TSS = (duration_hours * intensity_factor * 100)
     */
    private BigDecimal calculateTrainingStressScore(long durationSeconds, BigDecimal distanceMeters, BigDecimal elevationMeters) {
        if (durationSeconds == 0) {
            return BigDecimal.ZERO;
        }

        double durationHours = durationSeconds / 3600.0;
        double distance = distanceMeters.doubleValue();
        double elevation = elevationMeters.doubleValue();

        // Intensity factor based on distance/time ratio and elevation
        double speed = distance / durationSeconds; // m/s
        double intensityFactor = Math.min(1.0, speed / 3.0); // Normalize to ~3 m/s baseline

        // Elevation bonus (1m elevation = ~10m distance in terms of effort)
        double elevationBonus = elevation / 10.0;
        double effectiveDuration = durationHours * (1.0 + (elevationBonus / distance));

        // TSS = duration * intensity * 100
        double tss = effectiveDuration * intensityFactor * 100.0;

        return BigDecimal.valueOf(tss).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate rolling averages (ATL, CTL, TSB).
     */
    private void calculateRollingAverages(TrainingLoad trainingLoad, UUID userId, LocalDate date) {
        // Get last 7 days for ATL (Acute Training Load)
        LocalDate sevenDaysAgo = date.minusDays(6);
        List<TrainingLoad> last7Days = trainingLoadRepository
                .findByUserIdAndDateBetweenOrderByDateDesc(userId, sevenDaysAgo, date);

        BigDecimal atl = last7Days.stream()
                .map(tl -> tl.getTrainingStressScore() != null ? tl.getTrainingStressScore() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);

        // Get last 28 days for CTL (Chronic Training Load)
        LocalDate twentyEightDaysAgo = date.minusDays(27);
        List<TrainingLoad> last28Days = trainingLoadRepository
                .findByUserIdAndDateBetweenOrderByDateDesc(userId, twentyEightDaysAgo, date);

        BigDecimal ctl = last28Days.stream()
                .map(tl -> tl.getTrainingStressScore() != null ? tl.getTrainingStressScore() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(28), 2, RoundingMode.HALF_UP);

        // TSB = CTL - ATL (Training Stress Balance)
        BigDecimal tsb = ctl.subtract(atl);

        trainingLoad.setAcuteTrainingLoad(atl);
        trainingLoad.setChronicTrainingLoad(ctl);
        trainingLoad.setTrainingStressBalance(tsb);
    }

    /**
     * Get training load for a user over a date range.
     */
    @Transactional(readOnly = true)
    public List<TrainingLoad> getTrainingLoad(UUID userId, LocalDate startDate, LocalDate endDate) {
        return trainingLoadRepository.findByUserIdAndDateBetweenOrderByDateDesc(userId, startDate, endDate);
    }

    /**
     * Get recent training load (last 30 days).
     */
    @Transactional(readOnly = true)
    public List<TrainingLoad> getRecentTrainingLoad(UUID userId, int days) {
        LocalDate startDate = LocalDate.now().minusDays(days - 1);
        return trainingLoadRepository.findByUserIdSinceDate(userId, startDate);
    }

    /**
     * Get current form status for a user.
     */
    @Transactional(readOnly = true)
    public TrainingLoad.FormStatus getCurrentFormStatus(UUID userId) {
        return trainingLoadRepository.findFirstByUserIdOrderByDateDesc(userId)
                .map(TrainingLoad::getFormStatus)
                .orElse(TrainingLoad.FormStatus.UNKNOWN);
    }
}
