package org.operaton.fitpub.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.service.IndoorActivityMigrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for data migration and maintenance tasks.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final IndoorActivityMigrationService indoorActivityMigrationService;

    /**
     * Retroactively detect and update indoor activity flags for existing activities.
     * Re-parses all FIT files to detect indoor activities based on SubSport field.
     *
     * This is a one-time migration endpoint to update existing data.
     *
     * @return number of activities updated
     */
    @PostMapping("/migrate-indoor-flags")
    public ResponseEntity<Map<String, Object>> migrateIndoorFlags() {
        log.info("Admin: Starting indoor activity flag migration");

        int updatedCount = indoorActivityMigrationService.updateIndoorFlagsForExistingActivities();

        log.info("Admin: Indoor activity flag migration complete - {} activities updated", updatedCount);

        return ResponseEntity.ok(Map.of(
            "message", "Indoor activity flag migration complete",
            "activitiesUpdated", updatedCount
        ));
    }
}
