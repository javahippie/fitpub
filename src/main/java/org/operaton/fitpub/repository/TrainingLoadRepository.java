package org.operaton.fitpub.repository;

import org.operaton.fitpub.model.entity.TrainingLoad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrainingLoadRepository extends JpaRepository<TrainingLoad, UUID> {

    /**
     * Find training load for a specific user and date.
     */
    Optional<TrainingLoad> findByUserIdAndDate(UUID userId, LocalDate date);

    /**
     * Find training load for a user within a date range.
     */
    List<TrainingLoad> findByUserIdAndDateBetweenOrderByDateDesc(
            UUID userId,
            LocalDate startDate,
            LocalDate endDate
    );

    /**
     * Find recent training load entries for a user.
     */
    @Query("SELECT tl FROM TrainingLoad tl " +
           "WHERE tl.userId = :userId " +
           "ORDER BY tl.date DESC " +
           "LIMIT :limit")
    List<TrainingLoad> findRecentByUserId(@Param("userId") UUID userId, @Param("limit") int limit);

    /**
     * Find training load for the last N days.
     */
    @Query("SELECT tl FROM TrainingLoad tl " +
           "WHERE tl.userId = :userId " +
           "AND tl.date >= :startDate " +
           "ORDER BY tl.date DESC")
    List<TrainingLoad> findByUserIdSinceDate(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate
    );

    /**
     * Get the latest training load entry for a user.
     */
    Optional<TrainingLoad> findFirstByUserIdOrderByDateDesc(UUID userId);
}
