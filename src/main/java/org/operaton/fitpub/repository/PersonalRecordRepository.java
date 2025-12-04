package org.operaton.fitpub.repository;

import org.operaton.fitpub.model.entity.PersonalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PersonalRecordRepository extends JpaRepository<PersonalRecord, UUID> {

    /**
     * Find all personal records for a user.
     */
    List<PersonalRecord> findByUserIdOrderByAchievedAtDesc(UUID userId);

    /**
     * Find personal records for a user filtered by activity type.
     */
    List<PersonalRecord> findByUserIdAndActivityTypeOrderByAchievedAtDesc(
            UUID userId,
            PersonalRecord.ActivityType activityType
    );

    /**
     * Find a specific personal record by user, activity type, and record type.
     */
    Optional<PersonalRecord> findByUserIdAndActivityTypeAndRecordType(
            UUID userId,
            PersonalRecord.ActivityType activityType,
            PersonalRecord.RecordType recordType
    );

    /**
     * Get count of personal records set by a user.
     */
    @Query("SELECT COUNT(pr) FROM PersonalRecord pr WHERE pr.userId = :userId")
    long countByUserId(@Param("userId") UUID userId);

    /**
     * Get count of personal records set by a user in a date range.
     */
    @Query("SELECT COUNT(pr) FROM PersonalRecord pr " +
           "WHERE pr.userId = :userId " +
           "AND pr.achievedAt >= :startDate " +
           "AND pr.achievedAt < :endDate")
    long countByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate
    );

    /**
     * Find recent personal records for a user.
     */
    @Query("SELECT pr FROM PersonalRecord pr " +
           "WHERE pr.userId = :userId " +
           "ORDER BY pr.achievedAt DESC " +
           "LIMIT :limit")
    List<PersonalRecord> findRecentByUserId(@Param("userId") UUID userId, @Param("limit") int limit);
}
