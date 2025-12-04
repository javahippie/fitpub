package org.operaton.fitpub.repository;

import org.operaton.fitpub.model.entity.ActivitySummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ActivitySummaryRepository extends JpaRepository<ActivitySummary, UUID> {

    /**
     * Find a specific summary by user, period type, and start date.
     */
    Optional<ActivitySummary> findByUserIdAndPeriodTypeAndPeriodStart(
            UUID userId,
            ActivitySummary.PeriodType periodType,
            LocalDate periodStart
    );

    /**
     * Find summaries for a user by period type, ordered by most recent first.
     */
    List<ActivitySummary> findByUserIdAndPeriodTypeOrderByPeriodStartDesc(
            UUID userId,
            ActivitySummary.PeriodType periodType
    );

    /**
     * Find recent summaries for a user of a specific period type.
     */
    @Query("SELECT s FROM ActivitySummary s " +
           "WHERE s.userId = :userId " +
           "AND s.periodType = :periodType " +
           "ORDER BY s.periodStart DESC " +
           "LIMIT :limit")
    List<ActivitySummary> findRecentByUserIdAndPeriodType(
            @Param("userId") UUID userId,
            @Param("periodType") ActivitySummary.PeriodType periodType,
            @Param("limit") int limit
    );

    /**
     * Find summaries within a date range.
     */
    @Query("SELECT s FROM ActivitySummary s " +
           "WHERE s.userId = :userId " +
           "AND s.periodType = :periodType " +
           "AND s.periodStart >= :startDate " +
           "AND s.periodEnd <= :endDate " +
           "ORDER BY s.periodStart DESC")
    List<ActivitySummary> findByUserIdAndPeriodTypeAndDateRange(
            @Param("userId") UUID userId,
            @Param("periodType") ActivitySummary.PeriodType periodType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
