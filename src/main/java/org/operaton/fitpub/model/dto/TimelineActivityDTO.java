package org.operaton.fitpub.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.operaton.fitpub.model.entity.Activity;

import java.time.LocalDateTime;
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
    private boolean isLocal;

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
            .metrics(activity.getMetrics() != null ? ActivityMetricsSummary.fromMetrics(activity.getMetrics()) : null)
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
