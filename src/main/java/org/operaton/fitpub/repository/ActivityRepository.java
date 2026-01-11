package org.operaton.fitpub.repository;

import org.operaton.fitpub.model.entity.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Activity entities.
 */
@Repository
public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    /**
     * Find all activities for a specific user.
     *
     * @param userId the user ID
     * @return list of activities
     */
    List<Activity> findByUserIdOrderByStartedAtDesc(UUID userId);

    /**
     * Find all activities for a user within a date range.
     *
     * @param userId the user ID
     * @param startDate the start date
     * @param endDate the end date
     * @return list of activities
     */
    List<Activity> findByUserIdAndStartedAtBetweenOrderByStartedAtDesc(
        UUID userId,
        LocalDateTime startDate,
        LocalDateTime endDate
    );

    /**
     * Find all public activities for a user.
     *
     * @param userId the user ID
     * @param visibility the visibility level
     * @return list of activities
     */
    List<Activity> findByUserIdAndVisibilityOrderByStartedAtDesc(
        UUID userId,
        Activity.Visibility visibility
    );

    /**
     * Find activities for a user by visibility with pagination.
     *
     * @param userId the user ID
     * @param visibility the visibility level
     * @param pageable pagination parameters
     * @return page of activities
     */
    Page<Activity> findByUserIdAndVisibilityOrderByStartedAtDesc(
        UUID userId,
        Activity.Visibility visibility,
        Pageable pageable
    );

    /**
     * Find activities by type for a user.
     *
     * @param userId the user ID
     * @param activityType the activity type
     * @return list of activities
     */
    List<Activity> findByUserIdAndActivityTypeOrderByStartedAtDesc(
        UUID userId,
        Activity.ActivityType activityType
    );

    /**
     * Count activities for a user.
     *
     * @param userId the user ID
     * @return count of activities
     */
    long countByUserId(UUID userId);

    /**
     * Count activities for a user by visibility.
     *
     * @param userId the user ID
     * @param visibility the visibility level
     * @return count of activities
     */
    long countByUserIdAndVisibility(UUID userId, Activity.Visibility visibility);

    /**
     * Find an activity by ID and user ID.
     *
     * @param id the activity ID
     * @param userId the user ID
     * @return optional activity
     */
    Optional<Activity> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Delete all activities for a user.
     *
     * @param userId the user ID
     */
    void deleteByUserId(UUID userId);

    /**
     * Find activities for a user with pagination.
     *
     * @param userId the user ID
     * @param pageable pagination parameters
     * @return page of activities
     */
    Page<Activity> findByUserIdOrderByStartedAtDesc(UUID userId, Pageable pageable);

    /**
     * Find activities by user IDs and visibility with pagination.
     * Used for federated timeline.
     *
     * @param userIds list of user IDs
     * @param visibilities list of visibility values
     * @param pageable pagination parameters
     * @return page of activities
     */
    Page<Activity> findByUserIdInAndVisibilityInOrderByStartedAtDesc(
        List<UUID> userIds,
        List<Activity.Visibility> visibilities,
        Pageable pageable
    );

    /**
     * Find all public activities with pagination.
     * Used for public timeline.
     *
     * @param visibility the visibility level
     * @param pageable pagination parameters
     * @return page of activities
     */
    Page<Activity> findByVisibilityOrderByStartedAtDesc(
        Activity.Visibility visibility,
        Pageable pageable
    );

    /**
     * Count activities by user and activity type.
     */
    long countByUserIdAndActivityType(UUID userId, Activity.ActivityType activityType);

    /**
     * Sum total distance for a user.
     */
    @Query("SELECT COALESCE(SUM(a.totalDistance), 0) FROM Activity a WHERE a.userId = :userId")
    java.math.BigDecimal sumDistanceByUserId(@Param("userId") UUID userId);

    /**
     * Sum total elevation gain for a user.
     */
    @Query("SELECT COALESCE(SUM(a.elevationGain), 0) FROM Activity a WHERE a.userId = :userId")
    java.math.BigDecimal sumElevationGainByUserId(@Param("userId") UUID userId);

    /**
     * Count activities by user and start time before a specific time.
     */
    @Query("SELECT COUNT(a) FROM Activity a WHERE a.userId = :userId " +
           "AND FUNCTION('TIME', a.startedAt) < :time")
    long countByUserIdAndStartTimeBefore(@Param("userId") UUID userId, @Param("time") java.time.LocalTime time);

    /**
     * Count activities by user and start time after a specific time.
     */
    @Query("SELECT COUNT(a) FROM Activity a WHERE a.userId = :userId " +
           "AND FUNCTION('TIME', a.startedAt) > :time")
    long countByUserIdAndStartTimeAfter(@Param("userId") UUID userId, @Param("time") java.time.LocalTime time);

    /**
     * Count distinct activity types for a user.
     */
    @Query("SELECT COUNT(DISTINCT a.activityType) FROM Activity a WHERE a.userId = :userId")
    long countDistinctActivityTypesByUserId(@Param("userId") UUID userId);

    /**
     * Check if user has activity on a specific date.
     */
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Activity a " +
           "WHERE a.userId = :userId " +
           "AND FUNCTION('DATE', a.startedAt) = :date")
    boolean existsByUserIdAndDate(@Param("userId") UUID userId, @Param("date") java.time.LocalDate date);

    /**
     * Batch delete activities by IDs.
     * More efficient than deleting one by one.
     *
     * @param ids the list of activity IDs to delete
     * @return number of deleted activities
     */
    @Modifying
    @Query("DELETE FROM Activity a WHERE a.id IN :ids")
    int deleteByIdIn(@Param("ids") List<UUID> ids);

    /**
     * Find public timeline activities with user info and social stats in a single query.
     * OPTIMIZED: Eliminates N+1 query pattern (81 queries → 1 query for 20 activities)
     *
     * Returns Object[] with columns:
     * [0-16]: Activity fields (id, user_id, activity_type, title, description, started_at, ended_at,
     *         timezone, visibility, total_distance, total_duration_seconds, elevation_gain, elevation_loss,
     *         simplified_track, track_points_json, created_at, updated_at)
     * [17]: username
     * [18]: display_name
     * [19]: avatar_url
     * [20]: likes_count
     * [21]: comments_count
     * [22]: liked_by_current_user
     *
     * @param visibility the visibility level
     * @param currentUserId the current user ID (for liked status, can be null)
     * @param pageable pagination parameters
     * @return page of Object[] results
     */
    @Query(value = """
        SELECT
            a.id, a.user_id, a.activity_type, a.title, a.description, a.started_at, a.ended_at,
            a.timezone, a.visibility, a.total_distance, a.total_duration_seconds, a.elevation_gain, a.elevation_loss,
            a.simplified_track, a.track_points_json, a.created_at, a.updated_at,
            u.username, u.display_name, u.avatar_url,
            COUNT(DISTINCT l.id) AS likes_count,
            COUNT(DISTINCT c.id) AS comments_count,
            CASE WHEN ul.id IS NOT NULL THEN true ELSE false END AS liked_by_current_user
        FROM activities a
        INNER JOIN users u ON a.user_id = u.id
        LEFT JOIN likes l ON a.id = l.activity_id
        LEFT JOIN comments c ON a.id = c.activity_id AND c.deleted = false
        LEFT JOIN likes ul ON a.id = ul.activity_id AND ul.user_id = CAST(:currentUserId AS uuid)
        WHERE a.visibility = :visibility
        GROUP BY a.id, a.user_id, a.activity_type, a.title, a.description, a.started_at, a.ended_at,
                 a.timezone, a.visibility, a.total_distance, a.total_duration_seconds, a.elevation_gain, a.elevation_loss,
                 a.simplified_track, a.track_points_json, a.created_at, a.updated_at,
                 u.username, u.display_name, u.avatar_url, ul.id
        ORDER BY a.started_at DESC
        """, nativeQuery = true)
    Page<Object[]> findPublicTimelineWithStats(@Param("visibility") String visibility,
                                                @Param("currentUserId") UUID currentUserId,
                                                Pageable pageable);

    /**
     * Find user timeline activities with social stats in a single query.
     * OPTIMIZED: Eliminates N+1 query pattern
     *
     * @param userId the user ID
     * @param currentUserId the current user ID (for liked status, usually same as userId)
     * @param pageable pagination parameters
     * @return page of Object[] results (same structure as findPublicTimelineWithStats)
     */
    @Query(value = """
        SELECT
            a.id, a.user_id, a.activity_type, a.title, a.description, a.started_at, a.ended_at,
            a.timezone, a.visibility, a.total_distance, a.total_duration_seconds, a.elevation_gain, a.elevation_loss,
            a.simplified_track, a.track_points_json, a.created_at, a.updated_at,
            u.username, u.display_name, u.avatar_url,
            COUNT(DISTINCT l.id) AS likes_count,
            COUNT(DISTINCT c.id) AS comments_count,
            CASE WHEN ul.id IS NOT NULL THEN true ELSE false END AS liked_by_current_user
        FROM activities a
        INNER JOIN users u ON a.user_id = u.id
        LEFT JOIN likes l ON a.id = l.activity_id
        LEFT JOIN comments c ON a.id = c.activity_id AND c.deleted = false
        LEFT JOIN likes ul ON a.id = ul.activity_id AND ul.user_id = CAST(:currentUserId AS uuid)
        WHERE a.user_id = CAST(:userId AS uuid)
        GROUP BY a.id, a.user_id, a.activity_type, a.title, a.description, a.started_at, a.ended_at,
                 a.timezone, a.visibility, a.total_distance, a.total_duration_seconds, a.elevation_gain, a.elevation_loss,
                 a.simplified_track, a.track_points_json, a.created_at, a.updated_at,
                 u.username, u.display_name, u.avatar_url, ul.id
        ORDER BY a.started_at DESC
        """, nativeQuery = true)
    Page<Object[]> findUserTimelineWithStats(@Param("userId") UUID userId,
                                              @Param("currentUserId") UUID currentUserId,
                                              Pageable pageable);

    /**
     * Find federated timeline activities (from followed users) with social stats.
     * OPTIMIZED: Eliminates N+1 query pattern
     *
     * @param userIds list of user IDs to include
     * @param visibilities list of visibility levels
     * @param currentUserId the current user ID (for liked status)
     * @param pageable pagination parameters
     * @return page of Object[] results (same structure as findPublicTimelineWithStats)
     */
    @Query(value = """
        SELECT
            a.id, a.user_id, a.activity_type, a.title, a.description, a.started_at, a.ended_at,
            a.timezone, a.visibility, a.total_distance, a.total_duration_seconds, a.elevation_gain, a.elevation_loss,
            a.simplified_track, a.track_points_json, a.created_at, a.updated_at,
            u.username, u.display_name, u.avatar_url,
            COUNT(DISTINCT l.id) AS likes_count,
            COUNT(DISTINCT c.id) AS comments_count,
            CASE WHEN ul.id IS NOT NULL THEN true ELSE false END AS liked_by_current_user
        FROM activities a
        INNER JOIN users u ON a.user_id = u.id
        LEFT JOIN likes l ON a.id = l.activity_id
        LEFT JOIN comments c ON a.id = c.activity_id AND c.deleted = false
        LEFT JOIN likes ul ON a.id = ul.activity_id AND ul.user_id = CAST(:currentUserId AS uuid)
        WHERE a.user_id IN (:userIds)
          AND a.visibility IN (:visibilities)
        GROUP BY a.id, a.user_id, a.activity_type, a.title, a.description, a.started_at, a.ended_at,
                 a.timezone, a.visibility, a.total_distance, a.total_duration_seconds, a.elevation_gain, a.elevation_loss,
                 a.simplified_track, a.track_points_json, a.created_at, a.updated_at,
                 u.username, u.display_name, u.avatar_url, ul.id
        ORDER BY a.started_at DESC
        """, nativeQuery = true)
    Page<Object[]> findFederatedTimelineWithStats(@Param("userIds") List<UUID> userIds,
                                                    @Param("visibilities") List<String> visibilities,
                                                    @Param("currentUserId") UUID currentUserId,
                                                    Pageable pageable);

    /**
     * Find activities by source file format where raw activity file is present.
     * Used for retroactive data migration.
     *
     * @param sourceFileFormat the file format (e.g., "FIT", "GPX")
     * @return list of activities with raw files
     */
    @Query("SELECT a FROM Activity a WHERE a.sourceFileFormat = :sourceFileFormat AND a.rawActivityFile IS NOT NULL")
    List<Activity> findBySourceFileFormatAndRawActivityFileNotNull(@Param("sourceFileFormat") String sourceFileFormat);
}
