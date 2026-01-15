package net.javahippie.fitpub.service;

import lombok.extern.slf4j.Slf4j;
import net.javahippie.fitpub.model.dto.TimelineActivityDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Utility class for mapping native SQL query results (Object[]) to TimelineActivityDTO.
 *
 * This mapper is used for optimized timeline queries that use JOINs to fetch all data
 * in a single database roundtrip, eliminating the N+1 query problem.
 *
 * Expected Object[] structure from native query:
 * - Activity entity fields (all columns from activities table)
 * - username (from users.username)
 * - display_name (from users.display_name)
 * - avatar_url (from users.avatar_url)
 * - likes_count (COUNT aggregation)
 * - comments_count (COUNT aggregation)
 * - liked_by_current_user (CASE WHEN boolean)
 */
@Component
@Slf4j
public class TimelineResultMapper {

    /**
     * Map Object[] from native query to TimelineActivityDTO.
     *
     * The Object[] contains: [Activity columns..., username, display_name, avatar_url, likes_count, comments_count, liked_by_current_user]
     *
     * @param result Object array from native query
     * @return mapped TimelineActivityDTO
     */
    public TimelineActivityDTO mapToTimelineActivityDTO(Object[] result) {
        try {
            // Activity fields are at fixed positions based on the Activity entity column order
            // Note: These indices may need adjustment based on actual column order
            int idx = 0;

            UUID id = (UUID) result[idx++];
            UUID userId = (UUID) result[idx++];
            String activityType = (String) result[idx++];
            String title = (String) result[idx++];
            String description = (String) result[idx++];
            LocalDateTime startedAt = toLocalDateTime(result[idx++]);
            LocalDateTime endedAt = toLocalDateTime(result[idx++]);
            String timezone = (String) result[idx++];
            String visibility = (String) result[idx++];

            BigDecimal totalDistance = result[idx] != null ? (BigDecimal) result[idx] : null; idx++;
            Long totalDurationSeconds = result[idx] != null ? ((Number) result[idx]).longValue() : null; idx++;
            BigDecimal elevationGain = result[idx] != null ? (BigDecimal) result[idx] : null; idx++;
            BigDecimal elevationLoss = result[idx] != null ? (BigDecimal) result[idx] : null; idx++;

            // Skip geometry and json columns (simplified_track, track_points_json)
            idx++; // simplified_track
            idx++; // track_points_json

            // Note: metrics_id removed from query, no longer in result set

            LocalDateTime createdAt = toLocalDateTime(result[idx++]);
            LocalDateTime updatedAt = toLocalDateTime(result[idx++]);

            // User fields from JOIN
            String username = (String) result[idx++];
            String displayName = (String) result[idx++];
            String avatarUrl = (String) result[idx++];

            // Aggregated counts from JOIN
            Long likesCount = ((Number) result[idx++]).longValue();
            Long commentsCount = ((Number) result[idx++]).longValue();
            Boolean likedByCurrentUser = (Boolean) result[idx++];
            String activityLocation = (String) result[idx++];

            // Build DTO
            return TimelineActivityDTO.builder()
                .id(id)
                .activityType(activityType)
                .title(title)
                .description(description)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .totalDistance(totalDistance != null ? totalDistance.doubleValue() : null)
                .totalDurationSeconds(totalDurationSeconds)
                .elevationGain(elevationGain != null ? elevationGain.doubleValue() : null)
                .elevationLoss(elevationLoss != null ? elevationLoss.doubleValue() : null)
                .visibility(visibility)
                .createdAt(createdAt)
                .username(username)
                .displayName(displayName != null ? displayName : username)
                .avatarUrl(avatarUrl)
                .isLocal(true)
                .likesCount(likesCount)
                .commentsCount(commentsCount)
                .likedByCurrentUser(likedByCurrentUser)
                .hasGpsTrack(true)  // Will be refined based on actual data
                .activityLocation(activityLocation)
                .build();

        } catch (Exception e) {
            log.error("Error mapping timeline result to DTO", e);
            log.error("Result array length: {}", result != null ? result.length : "null");
            if (result != null) {
                for (int i = 0; i < result.length; i++) {
                    log.error("  [{}]: {} ({})", i, result[i], result[i] != null ? result[i].getClass().getName() : "null");
                }
            }
            throw new RuntimeException("Failed to map timeline result", e);
        }
    }

    /**
     * Convert SQL Timestamp to LocalDateTime.
     * Native SQL queries return java.sql.Timestamp, not java.time.LocalDateTime.
     *
     * @param obj the timestamp object from SQL result
     * @return LocalDateTime or null
     */
    private LocalDateTime toLocalDateTime(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) obj).toLocalDateTime();
        }
        if (obj instanceof java.time.LocalDateTime) {
            return (java.time.LocalDateTime) obj;
        }
        throw new IllegalArgumentException("Cannot convert " + obj.getClass() + " to LocalDateTime");
    }
}
