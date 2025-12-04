package org.operaton.fitpub.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing daily training load metrics for a user.
 * Used to calculate training stress, acute/chronic load, and recovery needs.
 */
@Entity
@Table(name = "training_load",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingLoad {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "activity_count")
    @Builder.Default
    private Integer activityCount = 0;

    @Column(name = "total_duration_seconds")
    @Builder.Default
    private Long totalDurationSeconds = 0L;

    @Column(name = "total_distance_meters", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalDistanceMeters = BigDecimal.ZERO;

    @Column(name = "total_elevation_gain_meters", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalElevationGainMeters = BigDecimal.ZERO;

    /**
     * Training Stress Score (TSS) - normalized training load for the day.
     * Calculated based on duration, intensity, and elevation.
     */
    @Column(name = "training_stress_score", precision = 6, scale = 2)
    private BigDecimal trainingStressScore;

    /**
     * Acute Training Load (ATL) - 7-day rolling average of TSS.
     * Represents recent training fatigue.
     */
    @Column(name = "acute_training_load", precision = 6, scale = 2)
    private BigDecimal acuteTrainingLoad;

    /**
     * Chronic Training Load (CTL) - 28-day rolling average of TSS.
     * Represents fitness level.
     */
    @Column(name = "chronic_training_load", precision = 6, scale = 2)
    private BigDecimal chronicTrainingLoad;

    /**
     * Training Stress Balance (TSB) = CTL - ATL.
     * Positive values indicate freshness/recovery.
     * Negative values indicate fatigue.
     */
    @Column(name = "training_stress_balance", precision = 6, scale = 2)
    private BigDecimal trainingStressBalance;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Get form status based on TSB.
     */
    public FormStatus getFormStatus() {
        if (trainingStressBalance == null) {
            return FormStatus.UNKNOWN;
        }

        BigDecimal tsb = trainingStressBalance;
        if (tsb.compareTo(BigDecimal.valueOf(5)) > 0) {
            return FormStatus.FRESH;
        } else if (tsb.compareTo(BigDecimal.valueOf(-10)) < 0) {
            return FormStatus.FATIGUED;
        } else {
            return FormStatus.OPTIMAL;
        }
    }

    public enum FormStatus {
        FRESH,      // Well rested, ready for hard training
        OPTIMAL,    // Good balance between fitness and fatigue
        FATIGUED,   // High fatigue, need recovery
        UNKNOWN     // Not enough data
    }
}
