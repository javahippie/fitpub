package org.operaton.fitpub.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing the processing result of an individual file within a batch import job.
 * Tracks success/failure status, associated activity, and error details if applicable.
 */
@Entity
@Table(name = "batch_import_file_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchImportFileResult {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "filename", nullable = false, length = 500)
    private String filename;

    @Column(name = "file_size")
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private FileStatus status;

    @Column(name = "activity_id")
    private UUID activityId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_type", length = 100)
    private String errorType;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    /**
     * Status of an individual file processing within a batch import.
     */
    public enum FileStatus {
        /** File is queued for processing */
        PENDING,
        /** File is currently being processed */
        PROCESSING,
        /** File was successfully processed and activity created */
        SUCCESS,
        /** File processing failed due to an error */
        FAILED,
        /** File was skipped (e.g., unsupported format) */
        SKIPPED
    }

    /**
     * Error type categories for failed file processing.
     */
    public static class ErrorType {
        public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
        public static final String PARSING_ERROR = "PARSING_ERROR";
        public static final String IO_ERROR = "IO_ERROR";
        public static final String UNSUPPORTED_FORMAT = "UNSUPPORTED_FORMAT";
        public static final String DATABASE_ERROR = "DATABASE_ERROR";
        public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";

        private ErrorType() {
            // Utility class
        }
    }

    /**
     * Checks if the file was successfully processed.
     *
     * @return true if status is SUCCESS
     */
    public boolean isSuccess() {
        return status == FileStatus.SUCCESS;
    }

    /**
     * Checks if the file processing failed.
     *
     * @return true if status is FAILED
     */
    public boolean isFailed() {
        return status == FileStatus.FAILED;
    }

    /**
     * Checks if the file was skipped.
     *
     * @return true if status is SKIPPED
     */
    public boolean isSkipped() {
        return status == FileStatus.SKIPPED;
    }

    /**
     * Sets default values before persisting.
     */
    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = FileStatus.PENDING;
        }
    }

    @Override
    public String toString() {
        return "BatchImportFileResult{" +
                "id=" + id +
                ", jobId=" + jobId +
                ", filename='" + filename + '\'' +
                ", status=" + status +
                ", activityId=" + activityId +
                ", errorType='" + errorType + '\'' +
                '}';
    }
}
