package org.operaton.fitpub.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entity storing calculated metrics and statistics for an activity.
 * Includes average/max values, pace information, and other derived data.
 */
@Entity
@Table(name = "activity_metrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    /**
     * Average speed in km/h (converted from m/s in FitParser/GpxParser).
     */
    @Column(name = "average_speed", precision = 8, scale = 2)
    private BigDecimal averageSpeed;

    /**
     * Maximum speed in km/h (converted from m/s in FitParser/GpxParser).
     */
    @Column(name = "max_speed", precision = 8, scale = 2)
    private BigDecimal maxSpeed;

    @Column(name = "average_pace_seconds")
    private Long averagePaceSeconds;

    @Column(name = "average_heart_rate")
    private Integer averageHeartRate;

    @Column(name = "max_heart_rate")
    private Integer maxHeartRate;

    @Column(name = "average_cadence")
    private Integer averageCadence;

    @Column(name = "max_cadence")
    private Integer maxCadence;

    @Column(name = "average_power")
    private Integer averagePower;

    @Column(name = "max_power")
    private Integer maxPower;

    @Column(name = "normalized_power")
    private Integer normalizedPower;

    @Column(name = "calories")
    private Integer calories;

    @Column(name = "average_temperature", precision = 5, scale = 2)
    private BigDecimal averageTemperature;

    @Column(name = "max_elevation", precision = 8, scale = 2)
    private BigDecimal maxElevation;

    @Column(name = "min_elevation", precision = 8, scale = 2)
    private BigDecimal minElevation;

    @Column(name = "total_ascent", precision = 8, scale = 2)
    private BigDecimal totalAscent;

    @Column(name = "total_descent", precision = 8, scale = 2)
    private BigDecimal totalDescent;

    @Column(name = "moving_time_seconds")
    private Long movingTimeSeconds;

    @Column(name = "stopped_time_seconds")
    private Long stoppedTimeSeconds;

    @Column(name = "total_steps")
    private Integer totalSteps;

    @Column(name = "training_stress_score")
    private BigDecimal trainingStressScore;
}
