package net.javahippie.fitpub.repository;

import net.javahippie.fitpub.model.entity.BatchImportFileResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for managing batch import file result entities.
 * Provides queries for retrieving file processing results within batch import jobs.
 */
@Repository
public interface BatchImportFileResultRepository extends JpaRepository<BatchImportFileResult, UUID> {

    /**
     * Finds all file results for a specific batch import job, ordered by processed date (newest first).
     *
     * @param jobId the batch import job ID
     * @return list of file results
     */
    List<BatchImportFileResult> findByJobIdOrderByProcessedAtDesc(UUID jobId);

    /**
     * Finds all file results for a specific batch import job with a specific status.
     *
     * @param jobId  the batch import job ID
     * @param status the file processing status
     * @return list of file results with that status
     */
    List<BatchImportFileResult> findByJobIdAndStatus(UUID jobId, BatchImportFileResult.FileStatus status);

    /**
     * Counts the number of file results for a job with a specific status.
     *
     * @param jobId  the batch import job ID
     * @param status the file processing status
     * @return count of files with that status
     */
    long countByJobIdAndStatus(UUID jobId, BatchImportFileResult.FileStatus status);

    /**
     * Finds all file results for a specific batch import job, ordered by filename.
     * Useful for displaying results in alphabetical order.
     *
     * @param jobId the batch import job ID
     * @return list of file results ordered by filename
     */
    List<BatchImportFileResult> findByJobIdOrderByFilenameAsc(UUID jobId);

    /**
     * Deletes all file results for a specific batch import job.
     * Typically handled by CASCADE DELETE, but provided for explicit cleanup if needed.
     *
     * @param jobId the batch import job ID
     */
    void deleteByJobId(UUID jobId);
}
