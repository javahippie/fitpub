package org.operaton.fitpub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.entity.Achievement;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.repository.AchievementRepository;
import org.operaton.fitpub.repository.ActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Service for managing achievements and badges.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final ActivityRepository activityRepository;

    /**
     * Check and award achievements for an activity.
     * Called after an activity is saved.
     *
     * @param activity the activity to check for achievements
     * @return list of newly earned achievements
     */
    @Transactional
    public List<Achievement> checkAndAwardAchievements(Activity activity) {
        List<Achievement> newAchievements = new ArrayList<>();

        if (activity.getUserId() == null) {
            return newAchievements;
        }

        UUID userId = activity.getUserId();

        // Check first activity achievements
        newAchievements.addAll(checkFirstActivityAchievements(userId, activity));

        // Check distance milestones
        newAchievements.addAll(checkDistanceMilestones(userId));

        // Check activity count milestones
        newAchievements.addAll(checkActivityCountMilestones(userId));

        // Check streak achievements
        newAchievements.addAll(checkStreakAchievements(userId));

        // Check time-based achievements
        newAchievements.addAll(checkTimeBasedAchievements(userId, activity));

        // Check elevation achievements
        newAchievements.addAll(checkElevationAchievements(userId, activity));

        // Check variety achievements
        newAchievements.addAll(checkVarietyAchievements(userId));

        // Check speed achievements
        newAchievements.addAll(checkSpeedAchievements(userId, activity));

        return newAchievements;
    }

    /**
     * Check first activity achievements.
     */
    private List<Achievement> checkFirstActivityAchievements(UUID userId, Activity activity) {
        List<Achievement> achievements = new ArrayList<>();

        // First activity overall
        long totalActivities = activityRepository.countByUserId(userId);
        if (totalActivities == 1) {
            achievements.add(awardAchievement(
                    userId,
                    Achievement.AchievementType.FIRST_ACTIVITY,
                    "First Steps",
                    "Completed your first activity!",
                    "🎉",
                    "#ff00ff",
                    activity.getId(),
                    null
            ));
        }

        // First activity by type
        String activityType = activity.getActivityType().name();
        long typeCount = activityRepository.countByUserIdAndActivityType(userId, activity.getActivityType());

        if (typeCount == 1) {
            Achievement.AchievementType achievementType = switch (activityType) {
                case "RUN" -> Achievement.AchievementType.FIRST_RUN;
                case "RIDE" -> Achievement.AchievementType.FIRST_RIDE;
                case "HIKE" -> Achievement.AchievementType.FIRST_HIKE;
                default -> null;
            };

            if (achievementType != null) {
                achievements.add(awardAchievement(
                        userId,
                        achievementType,
                        "First " + activityType.toLowerCase(),
                        "Completed your first " + activityType.toLowerCase() + "!",
                        getActivityEmoji(activityType),
                        "#00ffff",
                        activity.getId(),
                        null
                ));
            }
        }

        return achievements;
    }

    /**
     * Check distance milestone achievements.
     */
    private List<Achievement> checkDistanceMilestones(UUID userId) {
        List<Achievement> achievements = new ArrayList<>();

        // Calculate total distance
        BigDecimal totalDistance = activityRepository.sumDistanceByUserId(userId);
        if (totalDistance == null) {
            return achievements;
        }

        double totalKm = totalDistance.doubleValue() / 1000.0;

        // Check milestones
        Map<Double, Achievement.AchievementType> milestones = Map.of(
                10.0, Achievement.AchievementType.DISTANCE_10K,
                50.0, Achievement.AchievementType.DISTANCE_50K,
                100.0, Achievement.AchievementType.DISTANCE_100K,
                500.0, Achievement.AchievementType.DISTANCE_500K,
                1000.0, Achievement.AchievementType.DISTANCE_1000K
        );

        for (Map.Entry<Double, Achievement.AchievementType> entry : milestones.entrySet()) {
            if (totalKm >= entry.getKey() && !hasAchievement(userId, entry.getValue())) {
                achievements.add(awardAchievement(
                        userId,
                        entry.getValue(),
                        String.format("%.0f km Total", entry.getKey()),
                        String.format("Reached %.0f kilometers total distance!", entry.getKey()),
                        "🏃",
                        "#ffff00",
                        null,
                        Map.of("distance_km", entry.getKey())
                ));
            }
        }

        return achievements;
    }

    /**
     * Check activity count milestone achievements.
     */
    private List<Achievement> checkActivityCountMilestones(UUID userId) {
        List<Achievement> achievements = new ArrayList<>();

        long activityCount = activityRepository.countByUserId(userId);

        Map<Long, Achievement.AchievementType> milestones = Map.of(
                10L, Achievement.AchievementType.ACTIVITIES_10,
                50L, Achievement.AchievementType.ACTIVITIES_50,
                100L, Achievement.AchievementType.ACTIVITIES_100,
                500L, Achievement.AchievementType.ACTIVITIES_500,
                1000L, Achievement.AchievementType.ACTIVITIES_1000
        );

        for (Map.Entry<Long, Achievement.AchievementType> entry : milestones.entrySet()) {
            if (activityCount >= entry.getKey() && !hasAchievement(userId, entry.getValue())) {
                achievements.add(awardAchievement(
                        userId,
                        entry.getValue(),
                        String.format("%d Activities", entry.getKey()),
                        String.format("Completed %d activities!", entry.getKey()),
                        "💪",
                        "#ff6600",
                        null,
                        Map.of("activity_count", entry.getKey())
                ));
            }
        }

        return achievements;
    }

    /**
     * Check streak achievements (consecutive days).
     */
    private List<Achievement> checkStreakAchievements(UUID userId) {
        List<Achievement> achievements = new ArrayList<>();

        int currentStreak = calculateCurrentStreak(userId);

        Map<Integer, Achievement.AchievementType> streakMilestones = Map.of(
                7, Achievement.AchievementType.STREAK_7_DAYS,
                30, Achievement.AchievementType.STREAK_30_DAYS,
                100, Achievement.AchievementType.STREAK_100_DAYS,
                365, Achievement.AchievementType.STREAK_365_DAYS
        );

        for (Map.Entry<Integer, Achievement.AchievementType> entry : streakMilestones.entrySet()) {
            if (currentStreak >= entry.getKey() && !hasAchievement(userId, entry.getValue())) {
                achievements.add(awardAchievement(
                        userId,
                        entry.getValue(),
                        String.format("%d Day Streak", entry.getKey()),
                        String.format("Worked out %d days in a row!", entry.getKey()),
                        "🔥",
                        "#ff1493",
                        null,
                        Map.of("streak_days", entry.getKey())
                ));
            }
        }

        return achievements;
    }

    /**
     * Check time-based achievements (early bird, night owl, weekend warrior).
     */
    private List<Achievement> checkTimeBasedAchievements(UUID userId, Activity activity) {
        List<Achievement> achievements = new ArrayList<>();

        LocalTime startTime = activity.getStartedAt().toLocalTime();

        // Early bird (before 6am)
        if (startTime.isBefore(LocalTime.of(6, 0)) && !hasAchievement(userId, Achievement.AchievementType.EARLY_BIRD)) {
            long earlyActivities = activityRepository.countByUserIdAndStartTimeBefore(userId, LocalTime.of(6, 0));
            if (earlyActivities >= 5) {
                achievements.add(awardAchievement(
                        userId,
                        Achievement.AchievementType.EARLY_BIRD,
                        "Early Bird",
                        "Completed 5+ activities before 6am!",
                        "🌅",
                        "#ccff00",
                        activity.getId(),
                        Map.of("early_activities", earlyActivities)
                ));
            }
        }

        // Night owl (after 10pm)
        if (startTime.isAfter(LocalTime.of(22, 0)) && !hasAchievement(userId, Achievement.AchievementType.NIGHT_OWL)) {
            long lateActivities = activityRepository.countByUserIdAndStartTimeAfter(userId, LocalTime.of(22, 0));
            if (lateActivities >= 5) {
                achievements.add(awardAchievement(
                        userId,
                        Achievement.AchievementType.NIGHT_OWL,
                        "Night Owl",
                        "Completed 5+ activities after 10pm!",
                        "🦉",
                        "#9370db",
                        activity.getId(),
                        Map.of("late_activities", lateActivities)
                ));
            }
        }

        return achievements;
    }

    /**
     * Check elevation achievements.
     */
    private List<Achievement> checkElevationAchievements(UUID userId, Activity activity) {
        List<Achievement> achievements = new ArrayList<>();

        // Single activity elevation
        if (activity.getElevationGain() != null &&
            activity.getElevationGain().compareTo(BigDecimal.valueOf(1000)) >= 0 &&
            !hasAchievement(userId, Achievement.AchievementType.MOUNTAINEER_1000M)) {

            achievements.add(awardAchievement(
                    userId,
                    Achievement.AchievementType.MOUNTAINEER_1000M,
                    "Mountaineer",
                    "Climbed 1000m+ in a single activity!",
                    "⛰️",
                    "#8b4513",
                    activity.getId(),
                    Map.of("elevation_gain", activity.getElevationGain())
            ));
        }

        // Total elevation milestones
        BigDecimal totalElevation = activityRepository.sumElevationGainByUserId(userId);
        if (totalElevation != null) {
            double totalM = totalElevation.doubleValue();

            if (totalM >= 5000 && !hasAchievement(userId, Achievement.AchievementType.MOUNTAINEER_5000M)) {
                achievements.add(awardAchievement(
                        userId,
                        Achievement.AchievementType.MOUNTAINEER_5000M,
                        "Mountain Conqueror",
                        "Climbed 5000m total elevation!",
                        "🏔️",
                        "#4169e1",
                        null,
                        Map.of("total_elevation", totalM)
                ));
            }

            if (totalM >= 10000 && !hasAchievement(userId, Achievement.AchievementType.MOUNTAINEER_10000M)) {
                achievements.add(awardAchievement(
                        userId,
                        Achievement.AchievementType.MOUNTAINEER_10000M,
                        "Summit Master",
                        "Climbed 10000m total elevation!",
                        "🗻",
                        "#1e90ff",
                        null,
                        Map.of("total_elevation", totalM)
                ));
            }
        }

        return achievements;
    }

    /**
     * Check variety achievements.
     */
    private List<Achievement> checkVarietyAchievements(UUID userId) {
        List<Achievement> achievements = new ArrayList<>();

        long distinctActivityTypes = activityRepository.countDistinctActivityTypesByUserId(userId);

        if (distinctActivityTypes >= 3 && !hasAchievement(userId, Achievement.AchievementType.VARIETY_SEEKER)) {
            achievements.add(awardAchievement(
                    userId,
                    Achievement.AchievementType.VARIETY_SEEKER,
                    "Variety Seeker",
                    "Tried 3+ different activity types!",
                    "🌈",
                    "#ff69b4",
                    null,
                    Map.of("activity_types", distinctActivityTypes)
            ));
        }

        return achievements;
    }

    /**
     * Check speed achievements.
     */
    private List<Achievement> checkSpeedAchievements(UUID userId, Activity activity) {
        List<Achievement> achievements = new ArrayList<>();

        if (activity.getMetrics() != null && activity.getMetrics().getMaxSpeed() != null) {
            // Convert m/s to km/h
            double maxSpeedKmh = activity.getMetrics().getMaxSpeed().doubleValue() * 3.6;

            if (maxSpeedKmh >= 50 && !hasAchievement(userId, Achievement.AchievementType.SPEED_DEMON)) {
                achievements.add(awardAchievement(
                        userId,
                        Achievement.AchievementType.SPEED_DEMON,
                        "Speed Demon",
                        "Reached 50+ km/h!",
                        "⚡",
                        "#ffd700",
                        activity.getId(),
                        Map.of("max_speed_kmh", maxSpeedKmh)
                ));
            }
        }

        return achievements;
    }

    /**
     * Calculate current activity streak (consecutive days).
     */
    private int calculateCurrentStreak(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDate checkDate = today;
        int streak = 0;

        // Check backwards from today
        for (int i = 0; i < 365; i++) { // Max check 1 year
            boolean hasActivity = activityRepository.existsByUserIdAndDate(userId, checkDate);

            if (hasActivity) {
                streak++;
                checkDate = checkDate.minusDays(1);
            } else {
                // Allow one rest day if we already have a streak
                if (streak > 0 && i > 0) {
                    checkDate = checkDate.minusDays(1);
                    continue;
                }
                break;
            }
        }

        return streak;
    }

    /**
     * Check if user has already earned an achievement.
     */
    private boolean hasAchievement(UUID userId, Achievement.AchievementType achievementType) {
        return achievementRepository.existsByUserIdAndAchievementType(userId, achievementType);
    }

    /**
     * Award an achievement to a user.
     */
    private Achievement awardAchievement(UUID userId, Achievement.AchievementType achievementType,
                                        String name, String description, String icon, String color,
                                        UUID activityId, Map<String, Object> metadata) {
        Achievement achievement = Achievement.builder()
                .userId(userId)
                .achievementType(achievementType)
                .name(name)
                .description(description)
                .badgeIcon(icon)
                .badgeColor(color)
                .earnedAt(LocalDateTime.now())
                .activityId(activityId)
                .metadata(metadata)
                .build();

        achievementRepository.save(achievement);
        log.info("Achievement earned: {} by user {}", name, userId);
        return achievement;
    }

    /**
     * Get emoji for activity type.
     */
    private String getActivityEmoji(String activityType) {
        return switch (activityType.toUpperCase()) {
            case "RUN" -> "🏃";
            case "RIDE" -> "🚴";
            case "HIKE" -> "🥾";
            case "WALK" -> "🚶";
            case "SWIM" -> "🏊";
            default -> "💪";
        };
    }

    /**
     * Get all achievements for a user.
     */
    @Transactional(readOnly = true)
    public List<Achievement> getUserAchievements(UUID userId) {
        return achievementRepository.findByUserIdOrderByEarnedAtDesc(userId);
    }

    /**
     * Get achievement count for a user.
     */
    @Transactional(readOnly = true)
    public long getAchievementCount(UUID userId) {
        return achievementRepository.countByUserId(userId);
    }
}
