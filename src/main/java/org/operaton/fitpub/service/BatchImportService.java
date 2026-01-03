package org.operaton.fitpub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.BatchImportFileResult;
import org.operaton.fitpub.model.entity.BatchImportJob;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.ActivityRepository;
import org.operaton.fitpub.repository.BatchImportFileResultRepository;
import org.operaton.fitpub.repository.BatchImportJobRepository;
import org.operaton.fitpub.repository.UserRepository;
import org.operaton.fitpub.util.ByteArrayMultipartFile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service for managing batch imports of activity files from ZIP archives.
 * Handles asynchronous processing, progress tracking, and analytics recalculation.
 */
@Service
@Slf4j
public class BatchImportService {

    // Validation constants
    private static final long MAX_ZIP_SIZE = 500L * 1024 * 1024; // 500 MB
    private static final int MAX_FILES_IN_ZIP = 1000;
    private static final long MAX_INDIVIDUAL_FILE_SIZE = 50L * 1024 * 1024; // 50 MB

    private final BatchImportJobRepository batchImportJobRepository;
    private final BatchImportFileResultRepository batchImportFileResultRepository;
    private final ActivityFileService activityFileService;
    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final PersonalRecordService personalRecordService;
    private final AchievementService achievementService;
    private final HeatmapGridService heatmapGridService;
    private final TrainingLoadService trainingLoadService;
    private final ActivitySummaryService activitySummaryService;
    private final BatchImportService self;

    public BatchImportService(
            BatchImportJobRepository batchImportJobRepository,
            BatchImportFileResultRepository batchImportFileResultRepository,
            ActivityFileService activityFileService,
            ActivityRepository activityRepository,
            UserRepository userRepository,
            PersonalRecordService personalRecordService,
            AchievementService achievementService,
            HeatmapGridService heatmapGridService,
            TrainingLoadService trainingLoadService,
            ActivitySummaryService activitySummaryService,
            @org.springframework.context.annotation.Lazy BatchImportService self) {
        this.batchImportJobRepository = batchImportJobRepository;
        this.batchImportFileResultRepository = batchImportFileResultRepository;
        this.activityFileService = activityFileService;
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
        this.personalRecordService = personalRecordService;
        this.achievementService = achievementService;
        this.heatmapGridService = heatmapGridService;
        this.trainingLoadService = trainingLoadService;
        this.activitySummaryService = activitySummaryService;
        this.self = self;
    }

    /**
     * Creates a batch import job from an uploaded ZIP file.
     * Validates the ZIP, extracts file list, creates job and file result entities.
     *
     * @param zipFile the uploaded ZIP file
     * @param userId  the user ID
     * @return the created batch import job
     * @throws IllegalArgumentException if validation fails
     */
    @Transactional
    public BatchImportJob createBatchImportJob(MultipartFile zipFile, UUID userId) {
        log.info("Creating batch import job for user {} with file {}", userId, zipFile.getOriginalFilename());

        // Validate ZIP file
        validateZipFile(zipFile);

        // Extract file list from ZIP
        List<FileEntry> fileEntries;
        try {
            fileEntries = extractFileList(zipFile.getBytes());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read ZIP file: " + e.getMessage(), e);
        }

        if (fileEntries.isEmpty()) {
            throw new IllegalArgumentException("ZIP file contains no valid activity files (.fit or .gpx)");
        }

        if (fileEntries.size() > MAX_FILES_IN_ZIP) {
            throw new IllegalArgumentException(
                    String.format("ZIP file contains too many files (%d). Maximum allowed: %d",
                            fileEntries.size(), MAX_FILES_IN_ZIP)
            );
        }

        // Create batch import job
        BatchImportJob job = BatchImportJob.builder()
                .userId(userId)
                .filename(zipFile.getOriginalFilename())
                .totalFiles(fileEntries.size())
                .status(BatchImportJob.JobStatus.PENDING)
                .skipFederation(true)
                .build();

        job = batchImportJobRepository.save(job);

        // Create file result entries
        for (FileEntry entry : fileEntries) {
            BatchImportFileResult fileResult = BatchImportFileResult.builder()
                    .jobId(job.getId())
                    .filename(entry.getName())
                    .fileSize(entry.getSize())
                    .status(BatchImportFileResult.FileStatus.PENDING)
                    .build();

            batchImportFileResultRepository.save(fileResult);
        }

        log.info("Created batch import job {} with {} files", job.getId(), fileEntries.size());

        // Schedule async processing AFTER transaction commits to ensure job is visible in database
        final UUID jobId = job.getId();
        final byte[] zipBytes;
        try {
            zipBytes = zipFile.getBytes();
        } catch (IOException e) {
            log.error("Failed to read ZIP file bytes", e);
            markJobAsFailed(job.getId(), "Failed to read ZIP file: " + e.getMessage());
            throw new RuntimeException("Failed to read ZIP file", e);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("Transaction committed, starting async processing for job {}", jobId);
                try {
                    self.processBatchImportAsync(jobId, zipBytes);
                    log.info("Async processing started for job {}", jobId);
                } catch (Exception e) {
                    log.error("Failed to start async processing for job {}", jobId, e);
                    // Job will remain in PENDING state, user can retry
                }
            }
        });

        log.info("Registered async processing callback for job {} (will start after commit)", jobId);
        return job;
    }

    /**
     * Processes a batch import job asynchronously.
     * Runs in a background thread pool and processes files one by one.
     *
     * @param jobId   the batch import job ID
     * @param zipData the ZIP file data
     */
    @Async("batchImportExecutor")
    public void processBatchImportAsync(UUID jobId, byte[] zipData) {
        log.info("Starting async processing for batch import job {}", jobId);

        try {
            // Mark job as processing
            markJobAsProcessing(jobId);

            // Extract files from ZIP
            List<FileEntry> fileEntries = extractFileList(zipData);

            // Get job and file results
            BatchImportJob job = batchImportJobRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalStateException("Batch import job not found: " + jobId));

            List<BatchImportFileResult> fileResults = batchImportFileResultRepository.findByJobIdOrderByFilenameAsc(jobId);

            // Process each file
            for (int i = 0; i < fileEntries.size(); i++) {
                FileEntry entry = fileEntries.get(i);
                BatchImportFileResult fileResult = fileResults.stream()
                        .filter(fr -> fr.getFilename().equals(entry.getName()))
                        .findFirst()
                        .orElse(null);

                if (fileResult == null) {
                    log.warn("File result not found for {}, skipping", entry.getName());
                    continue;
                }

                log.info("Processing file {}/{}: {} ({} bytes)",
                        i + 1, fileEntries.size(), entry.getName(), entry.getSize());

                processIndividualFile(job, entry, fileResult);
            }

            // Phase 2: Batch recalculate analytics
            log.info("Phase 2: Recalculating analytics for batch import job {}", jobId);
            recalculateAnalyticsForJob(job);

            // Mark job as completed
            markJobAsCompleted(jobId);

            log.info("Batch import job {} completed successfully. Processed: {}, Success: {}, Failed: {}",
                    jobId, job.getProcessedFiles(), job.getSuccessCount(), job.getFailedCount());

        } catch (Exception e) {
            log.error("Batch import job {} failed with error", jobId, e);
            markJobAsFailed(jobId, e.getMessage());
        }
    }

    /**
     * Processes an individual file within a batch import.
     * Uses REQUIRES_NEW transaction for fault isolation.
     *
     * @param job        the batch import job
     * @param entry      the file entry
     * @param fileResult the file result entity
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processIndividualFile(BatchImportJob job, FileEntry entry, BatchImportFileResult fileResult) {
        try {
            // Mark file as processing
            fileResult.setStatus(BatchImportFileResult.FileStatus.PROCESSING);
            batchImportFileResultRepository.save(fileResult);

            // Create MultipartFile from file data
            ByteArrayMultipartFile file = new ByteArrayMultipartFile(
                    "file",
                    entry.getName(),
                    "application/octet-stream",
                    entry.getData()
            );

            // Process activity file with batch import mode (skip all side effects)
            Activity activity = activityFileService.processActivityFile(
                    file,
                    job.getUserId(),
                    null, // Auto-generate title
                    null, // No description
                    Activity.Visibility.PUBLIC,
                    ActivityFileService.ProcessingOptions.batchImportMode()
            );

            // Mark file as success
            fileResult.setStatus(BatchImportFileResult.FileStatus.SUCCESS);
            fileResult.setActivityId(activity.getId());
            fileResult.setProcessedAt(LocalDateTime.now());
            batchImportFileResultRepository.save(fileResult);

            // Increment job progress
            incrementJobProgress(job.getId(), true, false, false);

            log.debug("Successfully processed file {} -> activity {}", entry.getName(), activity.getId());

        } catch (Exception e) {
            log.warn("Failed to process file {}: {}", entry.getName(), e.getMessage());

            // Determine error type
            String errorType = determineErrorType(e);

            // Mark file as failed
            fileResult.setStatus(BatchImportFileResult.FileStatus.FAILED);
            fileResult.setErrorMessage(e.getMessage());
            fileResult.setErrorType(errorType);
            fileResult.setProcessedAt(LocalDateTime.now());
            batchImportFileResultRepository.save(fileResult);

            // Increment job progress
            incrementJobProgress(job.getId(), false, true, false);
        }
    }

    /**
     * Recalculates analytics for all activities in a batch import job.
     * This is Phase 2 of the batch import process.
     *
     * @param job the batch import job
     */
    @Transactional
    protected void recalculateAnalyticsForJob(BatchImportJob job) {
        log.info("Recalculating analytics for batch import job {} (user {})", job.getId(), job.getUserId());

        try {
            // Get all successfully imported activities
            List<BatchImportFileResult> successfulResults = batchImportFileResultRepository
                    .findByJobIdAndStatus(job.getId(), BatchImportFileResult.FileStatus.SUCCESS);

            List<UUID> activityIds = successfulResults.stream()
                    .map(BatchImportFileResult::getActivityId)
                    .filter(id -> id != null)
                    .toList();

            if (activityIds.isEmpty()) {
                log.info("No successful activities to process analytics for");
                return;
            }

            log.info("Recalculating analytics for {} activities", activityIds.size());

            // Fetch user for heatmap recalculation
            User user = userRepository.findById(job.getUserId())
                    .orElseThrow(() -> new IllegalStateException("User not found: " + job.getUserId()));

            // Recalculate heatmap (single full rebuild is more efficient than incremental)
            log.debug("Rebuilding user heatmap...");
            heatmapGridService.recalculateUserHeatmap(user);

            // Recalculate personal records for each activity
            log.debug("Recalculating personal records...");
            for (UUID activityId : activityIds) {
                Activity activity = activityRepository.findById(activityId).orElse(null);
                if (activity != null) {
                    personalRecordService.checkAndUpdatePersonalRecords(activity);
                }
            }

            // Recalculate achievements for each activity
            log.debug("Recalculating achievements...");
            for (UUID activityId : activityIds) {
                Activity activity = activityRepository.findById(activityId).orElse(null);
                if (activity != null) {
                    achievementService.checkAndAwardAchievements(activity);
                }
            }

            // Recalculate training load for each activity
            log.debug("Recalculating training load...");
            for (UUID activityId : activityIds) {
                Activity activity = activityRepository.findById(activityId).orElse(null);
                if (activity != null) {
                    trainingLoadService.updateTrainingLoad(activity);
                }
            }

            // Recalculate activity summaries (async)
            log.debug("Updating activity summaries...");
            for (UUID activityId : activityIds) {
                Activity activity = activityRepository.findById(activityId).orElse(null);
                if (activity != null) {
                    activitySummaryService.updateSummariesForActivity(activity);
                }
            }

            log.info("Analytics recalculation completed for batch import job {}", job.getId());

        } catch (Exception e) {
            log.error("Failed to recalculate analytics for batch import job {}", job.getId(), e);
            // Don't fail the job - analytics can be recalculated manually if needed
        }
    }

    /**
     * Increments job progress counters atomically.
     * Uses REQUIRES_NEW transaction to avoid blocking file processing.
     *
     * @param jobId   the job ID
     * @param success true if file was successful
     * @param failed  true if file failed
     * @param skipped true if file was skipped
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void incrementJobProgress(UUID jobId, boolean success, boolean failed, boolean skipped) {
        BatchImportJob job = batchImportJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Batch import job not found: " + jobId));

        job.setProcessedFiles(job.getProcessedFiles() + 1);

        if (success) {
            job.setSuccessCount(job.getSuccessCount() + 1);
        }
        if (failed) {
            job.setFailedCount(job.getFailedCount() + 1);
        }
        if (skipped) {
            job.setSkippedCount(job.getSkippedCount() + 1);
        }

        batchImportJobRepository.save(job);

        log.debug("Job {} progress: {}/{} (success: {}, failed: {}, skipped: {})",
                jobId, job.getProcessedFiles(), job.getTotalFiles(),
                job.getSuccessCount(), job.getFailedCount(), job.getSkippedCount());
    }

    /**
     * Marks a job as processing.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markJobAsProcessing(UUID jobId) {
        BatchImportJob job = batchImportJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Batch import job not found: " + jobId));

        job.setStatus(BatchImportJob.JobStatus.PROCESSING);
        job.setStartedAt(LocalDateTime.now());
        batchImportJobRepository.save(job);

        log.info("Marked batch import job {} as PROCESSING", jobId);
    }

    /**
     * Marks a job as completed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markJobAsCompleted(UUID jobId) {
        BatchImportJob job = batchImportJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Batch import job not found: " + jobId));

        job.setStatus(BatchImportJob.JobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        batchImportJobRepository.save(job);

        log.info("Marked batch import job {} as COMPLETED", jobId);
    }

    /**
     * Marks a job as failed with an error message.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markJobAsFailed(UUID jobId, String errorMessage) {
        BatchImportJob job = batchImportJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Batch import job not found: " + jobId));

        job.setStatus(BatchImportJob.JobStatus.FAILED);
        job.setErrorMessage(errorMessage);
        job.setCompletedAt(LocalDateTime.now());
        batchImportJobRepository.save(job);

        log.error("Marked batch import job {} as FAILED: {}", jobId, errorMessage);
    }

    /**
     * Cleans up old batch import jobs older than the specified retention period.
     *
     * @param retentionDays number of days to keep jobs
     * @return number of jobs deleted
     */
    @Transactional
    public int cleanupOldJobs(int retentionDays) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        List<BatchImportJob> oldJobs = batchImportJobRepository.findByCreatedAtBefore(cutoffDate);

        if (oldJobs.isEmpty()) {
            log.info("No batch import jobs older than {} days found", retentionDays);
            return 0;
        }

        log.info("Found {} batch import jobs older than {} days, deleting...", oldJobs.size(), retentionDays);

        for (BatchImportJob job : oldJobs) {
            batchImportJobRepository.delete(job);
            log.debug("Deleted batch import job {} (created {})", job.getId(), job.getCreatedAt());
        }

        log.info("Deleted {} old batch import jobs", oldJobs.size());
        return oldJobs.size();
    }

    /**
     * Validates ZIP file constraints.
     */
    private void validateZipFile(MultipartFile zipFile) {
        if (zipFile == null || zipFile.isEmpty()) {
            throw new IllegalArgumentException("ZIP file is required");
        }

        if (zipFile.getSize() > MAX_ZIP_SIZE) {
            throw new IllegalArgumentException(
                    String.format("ZIP file is too large (%d MB). Maximum allowed: %d MB",
                            zipFile.getSize() / (1024 * 1024),
                            MAX_ZIP_SIZE / (1024 * 1024))
            );
        }

        String filename = zipFile.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("File must be a ZIP archive (.zip)");
        }
    }

    /**
     * Extracts file list from ZIP archive.
     * Only includes .fit and .gpx files.
     */
    private List<FileEntry> extractFileList(byte[] zipData) throws IOException {
        List<FileEntry> fileEntries = new ArrayList<>();

        try (ByteArrayInputStream bais = new ByteArrayInputStream(zipData);
             ZipInputStream zis = new ZipInputStream(bais)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // Skip directories
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                // Skip hidden files and __MACOSX files
                if (name.startsWith("__MACOSX") || name.contains("/.")) {
                    continue;
                }

                // Extract just the filename (no path)
                String filename = name.substring(name.lastIndexOf('/') + 1);

                // Only accept .fit and .gpx files
                String lowerFilename = filename.toLowerCase();
                if (!lowerFilename.endsWith(".fit") && !lowerFilename.endsWith(".gpx")) {
                    log.debug("Skipping non-activity file: {}", filename);
                    continue;
                }

                // Check file size
                if (entry.getSize() > MAX_INDIVIDUAL_FILE_SIZE) {
                    log.warn("Skipping file {} - too large ({} MB, max: {} MB)",
                            filename, entry.getSize() / (1024 * 1024),
                            MAX_INDIVIDUAL_FILE_SIZE / (1024 * 1024));
                    continue;
                }

                // Read file data
                byte[] fileData = zis.readAllBytes();

                fileEntries.add(new FileEntry(filename, fileData.length, fileData));
            }
        }

        log.info("Extracted {} valid activity files from ZIP", fileEntries.size());
        return fileEntries;
    }

    /**
     * Determines error type from exception.
     */
    private String determineErrorType(Exception e) {
        String className = e.getClass().getSimpleName();

        if (className.contains("Validation")) {
            return BatchImportFileResult.ErrorType.VALIDATION_ERROR;
        } else if (className.contains("Parsing") || className.contains("Format")) {
            return BatchImportFileResult.ErrorType.PARSING_ERROR;
        } else if (className.contains("Unsupported")) {
            return BatchImportFileResult.ErrorType.UNSUPPORTED_FORMAT;
        } else if (className.contains("IO") || className.contains("File")) {
            return BatchImportFileResult.ErrorType.IO_ERROR;
        } else if (className.contains("SQL") || className.contains("Database") || className.contains("JPA")) {
            return BatchImportFileResult.ErrorType.DATABASE_ERROR;
        } else {
            return BatchImportFileResult.ErrorType.UNKNOWN_ERROR;
        }
    }

    /**
     * Internal class for file entries extracted from ZIP.
     */
    private static class FileEntry {
        private final String name;
        private final long size;
        private final byte[] data;

        public FileEntry(String name, long size, byte[] data) {
            this.name = name;
            this.size = size;
            this.data = data;
        }

        public String getName() {
            return name;
        }

        public long getSize() {
            return size;
        }

        public byte[] getData() {
            return data;
        }
    }
}
