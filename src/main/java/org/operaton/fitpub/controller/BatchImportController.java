package org.operaton.fitpub.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.entity.BatchImportFileResult;
import org.operaton.fitpub.model.entity.BatchImportJob;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.BatchImportFileResultRepository;
import org.operaton.fitpub.repository.BatchImportJobRepository;
import org.operaton.fitpub.repository.UserRepository;
import org.operaton.fitpub.service.BatchImportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for batch import operations.
 * Handles ZIP file uploads and progress tracking for batch activity imports.
 */
@RestController
@RequestMapping("/api/batch-import")
@RequiredArgsConstructor
@Slf4j
public class BatchImportController {

    private final BatchImportService batchImportService;
    private final BatchImportJobRepository batchImportJobRepository;
    private final BatchImportFileResultRepository batchImportFileResultRepository;
    private final UserRepository userRepository;

    /**
     * Uploads a ZIP file containing activity files and starts batch import processing.
     *
     * POST /api/batch-import/upload
     *
     * @param file           the ZIP file
     * @param authentication the authenticated user
     * @return 202 Accepted with job ID and initial status
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadZipFile(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        try {
            String username = authentication.getName();
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

            log.info("User {} uploading ZIP file for batch import: {}", username, file.getOriginalFilename());

            // Create batch import job (this also starts async processing)
            BatchImportJob job = batchImportService.createBatchImportJob(file, user.getId());

            // Return 202 Accepted with job details
            BatchImportJobStatusDTO status = mapJobToStatusDTO(job);

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(status);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid batch import request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to process batch import upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to process upload: " + e.getMessage()));
        }
    }

    /**
     * Gets the status of a batch import job.
     * Polled by frontend every 3 seconds for progress updates.
     *
     * GET /api/batch-import/jobs/{jobId}/status
     *
     * @param jobId          the job ID
     * @param authentication the authenticated user
     * @return job status with progress information
     */
    @GetMapping("/jobs/{jobId}/status")
    public ResponseEntity<?> getJobStatus(
            @PathVariable UUID jobId,
            Authentication authentication
    ) {
        try {
            String username = authentication.getName();
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

            // Get job and verify ownership
            BatchImportJob job = batchImportJobRepository.findByIdAndUserId(jobId, user.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Batch import job not found or access denied"));

            BatchImportJobStatusDTO status = mapJobToStatusDTO(job);

            return ResponseEntity.ok(status);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid job status request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get job status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to get job status: " + e.getMessage()));
        }
    }

    /**
     * Gets detailed file results for a batch import job.
     * Called when job completes to show success/failure details.
     *
     * GET /api/batch-import/jobs/{jobId}/files
     *
     * @param jobId          the job ID
     * @param authentication the authenticated user
     * @return list of file processing results
     */
    @GetMapping("/jobs/{jobId}/files")
    public ResponseEntity<?> getJobFileResults(
            @PathVariable UUID jobId,
            Authentication authentication
    ) {
        try {
            String username = authentication.getName();
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

            // Get job and verify ownership
            BatchImportJob job = batchImportJobRepository.findByIdAndUserId(jobId, user.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Batch import job not found or access denied"));

            // Get file results
            List<BatchImportFileResult> fileResults = batchImportFileResultRepository
                    .findByJobIdOrderByProcessedAtDesc(jobId);

            List<BatchImportFileResultDTO> resultDTOs = fileResults.stream()
                    .map(this::mapFileResultToDTO)
                    .toList();

            return ResponseEntity.ok(resultDTOs);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid file results request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get file results", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to get file results: " + e.getMessage()));
        }
    }

    /**
     * Lists recent batch import jobs for the authenticated user.
     *
     * GET /api/batch-import/jobs?page=0&size=10
     *
     * @param page           page number (default 0)
     * @param size           page size (default 10)
     * @param authentication the authenticated user
     * @return paginated list of batch import jobs
     */
    @GetMapping("/jobs")
    public ResponseEntity<?> listUserJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        try {
            String username = authentication.getName();
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

            Pageable pageable = PageRequest.of(page, size);
            Page<BatchImportJob> jobs = batchImportJobRepository.findByUserIdOrderByCreatedAtDesc(
                    user.getId(), pageable);

            Page<BatchImportJobStatusDTO> jobDTOs = jobs.map(this::mapJobToStatusDTO);

            return ResponseEntity.ok(jobDTOs);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid list jobs request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to list jobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to list jobs: " + e.getMessage()));
        }
    }

    /**
     * Maps BatchImportJob entity to DTO.
     */
    private BatchImportJobStatusDTO mapJobToStatusDTO(BatchImportJob job) {
        return new BatchImportJobStatusDTO(
                job.getId(),
                job.getFilename(),
                job.getStatus().name(),
                job.getTotalFiles(),
                job.getProcessedFiles(),
                job.getSuccessCount(),
                job.getFailedCount(),
                job.getSkippedCount(),
                job.getProgressPercentage(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getErrorMessage()
        );
    }

    /**
     * Maps BatchImportFileResult entity to DTO.
     */
    private BatchImportFileResultDTO mapFileResultToDTO(BatchImportFileResult result) {
        return new BatchImportFileResultDTO(
                result.getId(),
                result.getFilename(),
                result.getFileSize(),
                result.getStatus().name(),
                result.getActivityId(),
                result.getErrorMessage(),
                result.getErrorType(),
                result.getProcessedAt()
        );
    }

    /**
     * DTO for batch import job status.
     */
    public record BatchImportJobStatusDTO(
            UUID id,
            String filename,
            String status,
            Integer totalFiles,
            Integer processedFiles,
            Integer successCount,
            Integer failedCount,
            Integer skippedCount,
            Integer progressPercentage,
            LocalDateTime createdAt,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            String errorMessage
    ) {
    }

    /**
     * DTO for batch import file result.
     */
    public record BatchImportFileResultDTO(
            UUID id,
            String filename,
            Long fileSize,
            String status,
            UUID activityId,
            String errorMessage,
            String errorType,
            LocalDateTime processedAt
    ) {
    }

    /**
     * DTO for error responses.
     */
    public record ErrorResponse(String error) {
    }
}
