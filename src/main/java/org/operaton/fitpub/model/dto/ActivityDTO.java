package org.operaton.fitpub.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.operaton.fitpub.model.entity.Activity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for Activity data transfer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityDTO {

    private UUID id;
    private UUID userId;
    private String activityType;
    private String title;
    private String description;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String visibility;
    private BigDecimal totalDistance;
    private Long totalDurationSeconds;
    private BigDecimal elevationGain;
    private BigDecimal elevationLoss;
    private ActivityMetricsDTO metrics;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Creates a DTO from an Activity entity.
     */
    public static ActivityDTO fromEntity(Activity activity) {
        ActivityDTOBuilder builder = ActivityDTO.builder()
            .id(activity.getId())
            .userId(activity.getUserId())
            .activityType(activity.getActivityType().name())
            .title(activity.getTitle())
            .description(activity.getDescription())
            .startedAt(activity.getStartedAt())
            .endedAt(activity.getEndedAt())
            .visibility(activity.getVisibility().name())
            .totalDistance(activity.getTotalDistance())
            .elevationGain(activity.getElevationGain())
            .elevationLoss(activity.getElevationLoss())
            .createdAt(activity.getCreatedAt())
            .updatedAt(activity.getUpdatedAt());

        if (activity.getTotalDuration() != null) {
            builder.totalDurationSeconds(activity.getTotalDuration().getSeconds());
        }

        if (activity.getMetrics() != null) {
            builder.metrics(ActivityMetricsDTO.fromEntity(activity.getMetrics()));
        }

        return builder.build();
    }
}
