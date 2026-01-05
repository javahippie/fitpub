package org.operaton.fitpub.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.operaton.fitpub.model.entity.Activity;
import org.operaton.fitpub.model.entity.RemoteActivity;
import org.operaton.fitpub.model.entity.RemoteActor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * DTO for timeline activity items.
 * Represents an activity in the federated timeline.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineActivityDTO {

    private UUID id;
    private String activityType;
    private String title;
    private String description;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Double totalDistance;
    private Long totalDurationSeconds;
    private Double elevationGain;
    private Double elevationLoss;
    private String visibility;
    private LocalDateTime createdAt;

    // User information
    private String username;
    private String displayName;
    private String avatarUrl;

    @JsonProperty("isLocal")
    private boolean isLocal;

    // Remote activity fields (only populated for federated activities)
    private String activityUri;  // Full ActivityPub URI (for remote activities)
    private String mapImageUrl;  // Map image URL (for remote activities)

    // Social interaction counts
    private Long likesCount;
    private Long commentsCount;
    private Boolean likedByCurrentUser;

    // GPS track availability
    private Boolean hasGpsTrack;  // True if activity has GPS data

    // Metrics summary
    private ActivityMetricsSummary metrics;

    /**
     * Convert Activity entity to timeline DTO.
     */
    public static TimelineActivityDTO fromActivity(Activity activity, String username, String displayName, String avatarUrl) {
        return TimelineActivityDTO.builder()
            .id(activity.getId())
            .activityType(activity.getActivityType().name())
            .title(activity.getTitle())
            .description(activity.getDescription())
            .startedAt(activity.getStartedAt())
            .endedAt(activity.getEndedAt())
            .totalDistance(activity.getTotalDistance() != null ? activity.getTotalDistance().doubleValue() : null)
            .totalDurationSeconds(activity.getTotalDurationSeconds())
            .elevationGain(activity.getElevationGain() != null ? activity.getElevationGain().doubleValue() : null)
            .elevationLoss(activity.getElevationLoss() != null ? activity.getElevationLoss().doubleValue() : null)
            .visibility(activity.getVisibility().name())
            .createdAt(activity.getCreatedAt())
            .username(username)
            .displayName(displayName)
            .avatarUrl(avatarUrl)
            .isLocal(true)
            .hasGpsTrack(activity.getSimplifiedTrack() != null)
            .metrics(activity.getMetrics() != null ? ActivityMetricsSummary.fromMetrics(activity.getMetrics()) : null)
            .build();
    }

    /**
     * Convert RemoteActivity entity to timeline DTO.
     * Used for displaying federated activities from other FitPub instances.
     */
    public static TimelineActivityDTO fromRemoteActivity(RemoteActivity remote, RemoteActor actor) {
        // Create metrics summary from remote activity fields
        ActivityMetricsSummary metrics = ActivityMetricsSummary.builder()
            .averageHeartRate(remote.getAverageHeartRate())
            .averageSpeed(remote.getAverageSpeed())
            .maxSpeed(remote.getMaxSpeed())
            .averagePaceSeconds(remote.getAveragePaceSeconds())
            .calories(remote.getCalories())
            .build();

        return TimelineActivityDTO.builder()
            .id(remote.getId())
            .activityType(remote.getActivityType() != null ? remote.getActivityType() : "UNKNOWN")
            .title(remote.getTitle())
            .description(remote.getDescription())
            .startedAt(remote.getPublishedAt() != null
                ? LocalDateTime.ofInstant(remote.getPublishedAt(), ZoneId.systemDefault())
                : null)
            .endedAt(null) // Not available for remote activities
            .totalDistance(remote.getTotalDistance() != null ? remote.getTotalDistance().doubleValue() : null)
            .totalDurationSeconds(remote.getTotalDurationSeconds())
            .elevationGain(remote.getElevationGain() != null ? remote.getElevationGain().doubleValue() : null)
            .elevationLoss(null) // Not available for remote activities
            .visibility(remote.getVisibility() != null ? remote.getVisibility().name() : "PUBLIC")
            .createdAt(remote.getCreatedAt())
            .username(actor.getUsername())
            .displayName(actor.getDisplayName() != null ? actor.getDisplayName() : actor.getUsername())
            .avatarUrl(actor.getAvatarUrl())
            .isLocal(false)
            .activityUri(remote.getActivityUri())
            .mapImageUrl(remote.getMapImageUrl())
            .hasGpsTrack(remote.getMapImageUrl() != null)  // Remote activity has GPS if it has a map image
            .metrics(metrics)
            .build();
    }

    /**
     * Summary of activity metrics for timeline display.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityMetricsSummary {
        private Integer averageHeartRate;
        private Integer maxHeartRate;
        private Double averageSpeed;
        private Double maxSpeed;
        private Long averagePaceSeconds;
        private Integer averagePower;
        private Integer calories;

        public static ActivityMetricsSummary fromMetrics(org.operaton.fitpub.model.entity.ActivityMetrics metrics) {
            return ActivityMetricsSummary.builder()
                .averageHeartRate(metrics.getAverageHeartRate())
                .maxHeartRate(metrics.getMaxHeartRate())
                .averageSpeed(metrics.getAverageSpeed() != null ? metrics.getAverageSpeed().doubleValue() : null)
                .maxSpeed(metrics.getMaxSpeed() != null ? metrics.getMaxSpeed().doubleValue() : null)
                .averagePaceSeconds(metrics.getAveragePaceSeconds())
                .averagePower(metrics.getAveragePower())
                .calories(metrics.getCalories())
                .build();
        }
    }
}
