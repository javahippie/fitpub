package org.operaton.fitpub.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.service.BatchImportService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for cleaning up old batch import jobs.
 * Runs daily at 3 AM to delete jobs older than the retention period.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchImportCleanupScheduler {

    private static final int RETENTION_DAYS = 7;

    private final BatchImportService batchImportService;

    /**
     * Scheduled task to cleanup old batch import jobs.
     * Runs daily at 3:00 AM server time.
     * Deletes jobs (and their file results via CASCADE DELETE) older than 7 days.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupOldBatchImports() {
        log.info("Starting scheduled cleanup of batch import jobs older than {} days", RETENTION_DAYS);

        try {
            int deletedCount = batchImportService.cleanupOldJobs(RETENTION_DAYS);

            if (deletedCount > 0) {
                log.info("Batch import cleanup completed. Deleted {} jobs", deletedCount);
            } else {
                log.info("Batch import cleanup completed. No jobs to delete");
            }

        } catch (Exception e) {
            log.error("Batch import cleanup failed", e);
        }
    }
}
