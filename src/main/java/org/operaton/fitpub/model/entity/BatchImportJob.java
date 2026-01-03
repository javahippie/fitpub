package org.operaton.fitpub.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a batch import job for processing ZIP files containing multiple activity files.
 * Tracks overall progress, status, and results of the batch operation.
 */
@Entity
@Table(name = "batch_import_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "filename", nullable = false, length = 500)
    private String filename;

    @Column(name = "total_files", nullable = false)
    private Integer totalFiles;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private JobStatus status;

    @Column(name = "processed_files", nullable = false)
    @Builder.Default
    private Integer processedFiles = 0;

    @Column(name = "success_count", nullable = false)
    @Builder.Default
    private Integer successCount = 0;

    @Column(name = "failed_count", nullable = false)
    @Builder.Default
    private Integer failedCount = 0;

    @Column(name = "skipped_count", nullable = false)
    @Builder.Default
    private Integer skippedCount = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "skip_federation", nullable = false)
    @Builder.Default
    private Boolean skipFederation = true;

    @OneToMany(mappedBy = "jobId", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<BatchImportFileResult> fileResults = new ArrayList<>();

    /**
     * Status of the batch import job.
     */
    public enum JobStatus {
        /** Job created but not yet started */
        PENDING,
        /** Job is currently being processed */
        PROCESSING,
        /** Job completed successfully (all files processed, some may have failed) */
        COMPLETED,
        /** Job failed catastrophically (e.g., ZIP corruption, database error) */
        FAILED,
        /** Job was cancelled by user or system */
        CANCELLED
    }

    /**
     * Calculates the progress percentage of the job.
     *
     * @return progress as a value between 0 and 100
     */
    public int getProgressPercentage() {
        if (totalFiles == null || totalFiles == 0) {
            return 0;
        }
        if (processedFiles == null) {
            return 0;
        }
        return (int) Math.round((processedFiles * 100.0) / totalFiles);
    }

    /**
     * Checks if the job is in a terminal state (completed, failed, or cancelled).
     *
     * @return true if job is finished
     */
    public boolean isFinished() {
        return status == JobStatus.COMPLETED || status == JobStatus.FAILED || status == JobStatus.CANCELLED;
    }

    /**
     * Checks if the job is currently being processed.
     *
     * @return true if job is in processing state
     */
    public boolean isProcessing() {
        return status == JobStatus.PROCESSING;
    }

    /**
     * Sets default values before persisting.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = JobStatus.PENDING;
        }
        if (processedFiles == null) {
            processedFiles = 0;
        }
        if (successCount == null) {
            successCount = 0;
        }
        if (failedCount == null) {
            failedCount = 0;
        }
        if (skippedCount == null) {
            skippedCount = 0;
        }
        if (skipFederation == null) {
            skipFederation = true;
        }
    }

    @Override
    public String toString() {
        return "BatchImportJob{" +
                "id=" + id +
                ", userId=" + userId +
                ", filename='" + filename + '\'' +
                ", status=" + status +
                ", progress=" + getProgressPercentage() + "%" +
                ", processed=" + processedFiles + "/" + totalFiles +
                ", success=" + successCount +
                ", failed=" + failedCount +
                '}';
    }
}
