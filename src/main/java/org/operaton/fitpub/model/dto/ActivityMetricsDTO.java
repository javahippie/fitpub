package org.operaton.fitpub.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.operaton.fitpub.model.entity.ActivityMetrics;

import java.math.BigDecimal;

/**
 * DTO for ActivityMetrics data transfer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityMetricsDTO {

    private BigDecimal averageSpeed;
    private BigDecimal maxSpeed;
    private Long averagePaceSeconds;
    private Integer averageHeartRate;
    private Integer maxHeartRate;
    private Integer averageCadence;
    private Integer maxCadence;
    private Integer averagePower;
    private Integer maxPower;
    private Integer normalizedPower;
    private Integer calories;
    private BigDecimal averageTemperature;
    private BigDecimal maxElevation;
    private BigDecimal minElevation;
    private BigDecimal totalAscent;
    private BigDecimal totalDescent;
    private Long movingTimeSeconds;
    private Long stoppedTimeSeconds;
    private Integer totalSteps;
    private BigDecimal trainingStressScore;

    /**
     * Creates a DTO from an ActivityMetrics entity.
     */
    public static ActivityMetricsDTO fromEntity(ActivityMetrics metrics) {
        ActivityMetricsDTOBuilder builder = ActivityMetricsDTO.builder()
            .averageSpeed(metrics.getAverageSpeed())
            .maxSpeed(metrics.getMaxSpeed())
            .averageHeartRate(metrics.getAverageHeartRate())
            .maxHeartRate(metrics.getMaxHeartRate())
            .averageCadence(metrics.getAverageCadence())
            .maxCadence(metrics.getMaxCadence())
            .averagePower(metrics.getAveragePower())
            .maxPower(metrics.getMaxPower())
            .normalizedPower(metrics.getNormalizedPower())
            .calories(metrics.getCalories())
            .averageTemperature(metrics.getAverageTemperature())
            .maxElevation(metrics.getMaxElevation())
            .minElevation(metrics.getMinElevation())
            .totalAscent(metrics.getTotalAscent())
            .totalDescent(metrics.getTotalDescent())
            .totalSteps(metrics.getTotalSteps())
            .trainingStressScore(metrics.getTrainingStressScore());

        if (metrics.getAveragePaceSeconds() != null) {
            builder.averagePaceSeconds(metrics.getAveragePaceSeconds());
        }

        if (metrics.getMovingTimeSeconds() != null) {
            builder.movingTimeSeconds(metrics.getMovingTimeSeconds());
        }

        if (metrics.getStoppedTimeSeconds() != null) {
            builder.stoppedTimeSeconds(metrics.getStoppedTimeSeconds());
        }

        return builder.build();
    }
}
