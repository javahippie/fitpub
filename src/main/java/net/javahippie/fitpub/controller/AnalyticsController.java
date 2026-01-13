package net.javahippie.fitpub.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javahippie.fitpub.model.entity.Achievement;
import net.javahippie.fitpub.model.entity.ActivitySummary;
import net.javahippie.fitpub.model.entity.PersonalRecord;
import net.javahippie.fitpub.model.entity.TrainingLoad;
import net.javahippie.fitpub.model.entity.User;
import net.javahippie.fitpub.service.AchievementService;
import net.javahippie.fitpub.service.ActivitySummaryService;
import net.javahippie.fitpub.service.PersonalRecordService;
import net.javahippie.fitpub.service.TrainingLoadService;
import net.javahippie.fitpub.model.entity.*;
import net.javahippie.fitpub.repository.UserRepository;
import net.javahippie.fitpub.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for analytics and statistics.
 * Provides personal records, achievements, training load, and summaries.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final PersonalRecordService personalRecordService;
    private final AchievementService achievementService;
    private final TrainingLoadService trainingLoadService;
    private final ActivitySummaryService activitySummaryService;
    private final UserRepository userRepository;

    /**
     * Get user ID from authenticated user.
     */
    private UUID getUserId(UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userDetails.getUsername()));
        return user.getId();
    }

    /**
     * Get analytics dashboard data (overview).
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = getUserId(userDetails);

        Map<String, Object> dashboard = new HashMap<>();

        // Personal records count
        long prCount = personalRecordService.getPersonalRecordCount(userId);
        dashboard.put("personalRecordsCount", prCount);

        // Achievements count
        long achievementCount = achievementService.getAchievementCount(userId);
        dashboard.put("achievementsCount", achievementCount);

        // Recent personal records (last 5)
        List<PersonalRecord> recentPRs = personalRecordService.getPersonalRecords(userId)
                .stream()
                .limit(5)
                .toList();
        dashboard.put("recentPersonalRecords", recentPRs);

        // Recent achievements (last 5)
        List<Achievement> recentAchievements = achievementService.getUserAchievements(userId)
                .stream()
                .limit(5)
                .toList();
        dashboard.put("recentAchievements", recentAchievements);

        // Current form status
        TrainingLoad.FormStatus formStatus = trainingLoadService.getCurrentFormStatus(userId);
        dashboard.put("formStatus", formStatus);

        // Current week summary
        ActivitySummary currentWeek = activitySummaryService.getCurrentWeekSummary(userId);
        dashboard.put("currentWeekSummary", currentWeek);

        // Current month summary
        ActivitySummary currentMonth = activitySummaryService.getCurrentMonthSummary(userId);
        dashboard.put("currentMonthSummary", currentMonth);

        return ResponseEntity.ok(dashboard);
    }

    /**
     * Get all personal records for the authenticated user.
     */
    @GetMapping("/personal-records")
    public ResponseEntity<List<PersonalRecord>> getPersonalRecords(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String activityType) {

        UUID userId = getUserId(userDetails);

        List<PersonalRecord> records;
        if (activityType != null) {
            PersonalRecord.ActivityType type = PersonalRecord.ActivityType.valueOf(activityType.toUpperCase());
            records = personalRecordService.getPersonalRecordsByType(userId, type);
        } else {
            records = personalRecordService.getPersonalRecords(userId);
        }

        return ResponseEntity.ok(records);
    }

    /**
     * Get all achievements for the authenticated user.
     */
    @GetMapping("/achievements")
    public ResponseEntity<List<Achievement>> getAchievements(
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = getUserId(userDetails);
        List<Achievement> achievements = achievementService.getUserAchievements(userId);

        return ResponseEntity.ok(achievements);
    }

    /**
     * Get training load for a date range.
     */
    @GetMapping("/training-load")
    public ResponseEntity<List<TrainingLoad>> getTrainingLoad(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer days) {

        UUID userId = getUserId(userDetails);

        List<TrainingLoad> trainingLoad;
        if (days != null && days > 0) {
            trainingLoad = trainingLoadService.getRecentTrainingLoad(userId, days);
        } else {
            // Default to last 30 days
            trainingLoad = trainingLoadService.getRecentTrainingLoad(userId, 30);
        }

        return ResponseEntity.ok(trainingLoad);
    }

    /**
     * Get training load for specific date range.
     */
    @GetMapping("/training-load/range")
    public ResponseEntity<List<TrainingLoad>> getTrainingLoadRange(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        UUID userId = getUserId(userDetails);
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        List<TrainingLoad> trainingLoad = trainingLoadService.getTrainingLoad(userId, start, end);

        return ResponseEntity.ok(trainingLoad);
    }

    /**
     * Get current form status.
     */
    @GetMapping("/form-status")
    public ResponseEntity<Map<String, Object>> getFormStatus(
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = getUserId(userDetails);
        TrainingLoad.FormStatus formStatus = trainingLoadService.getCurrentFormStatus(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("formStatus", formStatus);
        response.put("description", getFormStatusDescription(formStatus));

        return ResponseEntity.ok(response);
    }

    /**
     * Get weekly summaries.
     */
    @GetMapping("/summaries/weekly")
    public ResponseEntity<List<ActivitySummary>> getWeeklySummaries(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "12") int weeks) {

        UUID userId = getUserId(userDetails);
        List<ActivitySummary> summaries = activitySummaryService.getWeeklySummaries(userId, weeks);

        return ResponseEntity.ok(summaries);
    }

    /**
     * Get monthly summaries.
     */
    @GetMapping("/summaries/monthly")
    public ResponseEntity<List<ActivitySummary>> getMonthlySummaries(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "12") int months) {

        UUID userId = getUserId(userDetails);
        List<ActivitySummary> summaries = activitySummaryService.getMonthlySummaries(userId, months);

        return ResponseEntity.ok(summaries);
    }

    /**
     * Get yearly summaries.
     */
    @GetMapping("/summaries/yearly")
    public ResponseEntity<List<ActivitySummary>> getYearlySummaries(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "5") int years) {

        UUID userId = getUserId(userDetails);
        List<ActivitySummary> summaries = activitySummaryService.getYearlySummaries(userId, years);

        return ResponseEntity.ok(summaries);
    }

    /**
     * Get current week summary.
     */
    @GetMapping("/summaries/current-week")
    public ResponseEntity<ActivitySummary> getCurrentWeekSummary(
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = getUserId(userDetails);
        ActivitySummary summary = activitySummaryService.getCurrentWeekSummary(userId);

        if (summary == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(summary);
    }

    /**
     * Get current month summary.
     */
    @GetMapping("/summaries/current-month")
    public ResponseEntity<ActivitySummary> getCurrentMonthSummary(
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = getUserId(userDetails);
        ActivitySummary summary = activitySummaryService.getCurrentMonthSummary(userId);

        if (summary == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(summary);
    }

    /**
     * Get form status description.
     */
    private String getFormStatusDescription(TrainingLoad.FormStatus status) {
        return switch (status) {
            case FRESH -> "You're well rested and ready for hard training!";
            case OPTIMAL -> "Good balance between fitness and fatigue.";
            case FATIGUED -> "High fatigue detected. Consider taking a rest day.";
            case UNKNOWN -> "Not enough data to calculate form status.";
        };
    }
}
