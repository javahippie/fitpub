package org.operaton.fitpub.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.UserRepository;
import org.operaton.fitpub.service.HeatmapGridService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled task to recalculate user heatmaps nightly.
 * Ensures heatmap data stays in sync with activities even if incremental updates fail.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HeatmapRecalculationScheduler {

    private final HeatmapGridService heatmapGridService;
    private final UserRepository userRepository;

    /**
     * Recalculate heatmaps for all users.
     * Runs daily at 3 AM server time.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void recalculateAllUserHeatmaps() {
        log.info("Starting nightly heatmap recalculation for all users");
        long startTime = System.currentTimeMillis();

        List<User> users = userRepository.findAll();
        log.info("Found {} users to process", users.size());

        int successCount = 0;
        int errorCount = 0;

        for (User user : users) {
            try {
                heatmapGridService.recalculateUserHeatmap(user);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to recalculate heatmap for user {}", user.getUsername(), e);
                errorCount++;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Heatmap recalculation completed in {}ms. Success: {}, Errors: {}",
                duration, successCount, errorCount);
    }
}
