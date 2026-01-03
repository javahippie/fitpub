package org.operaton.fitpub.repository;

import org.operaton.fitpub.model.entity.BatchImportJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing batch import job entities.
 * Provides queries for job tracking, cleanup, and user-specific job retrieval.
 */
@Repository
public interface BatchImportJobRepository extends JpaRepository<BatchImportJob, UUID> {

    /**
     * Finds all batch import jobs for a specific user, ordered by creation date (newest first).
     *
     * @param userId   the user ID
     * @param pageable pagination information
     * @return page of batch import jobs
     */
    Page<BatchImportJob> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Finds a batch import job by ID and user ID (for authorization checks).
     *
     * @param id     the job ID
     * @param userId the user ID
     * @return optional batch import job
     */
    Optional<BatchImportJob> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Finds all batch import jobs created before a specific date.
     * Used for cleanup operations (e.g., deleting jobs older than N days).
     *
     * @param cutoffDate the cutoff date
     * @return list of old batch import jobs
     */
    List<BatchImportJob> findByCreatedAtBefore(LocalDateTime cutoffDate);

    /**
     * Finds stalled batch import jobs that have been in PROCESSING state for too long.
     * A job is considered stalled if it's been processing for more than the timeout period.
     *
     * @param timeout the timeout threshold (jobs started before this are considered stalled)
     * @return list of stalled jobs
     */
    @Query("SELECT j FROM BatchImportJob j WHERE j.status = 'PROCESSING' AND j.startedAt < :timeout")
    List<BatchImportJob> findStalledJobs(@Param("timeout") LocalDateTime timeout);

    /**
     * Counts the number of batch import jobs for a user in a specific status.
     *
     * @param userId the user ID
     * @param status the job status
     * @return count of jobs
     */
    long countByUserIdAndStatus(UUID userId, BatchImportJob.JobStatus status);

    /**
     * Finds all batch import jobs in a specific status.
     *
     * @param status the job status
     * @return list of jobs in that status
     */
    List<BatchImportJob> findByStatus(BatchImportJob.JobStatus status);
}
