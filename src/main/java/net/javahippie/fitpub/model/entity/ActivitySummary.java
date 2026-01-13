package net.javahippie.fitpub.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing pre-calculated activity summaries for a time period.
 * Used for performance optimization of weekly/monthly/yearly statistics.
 */
@Entity
@Table(name = "activity_summaries",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "period_type", "period_start"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivitySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "period_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PeriodType periodType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

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

    @Column(name = "avg_speed_mps", precision = 6, scale = 2)
    private BigDecimal avgSpeedMps;

    @Column(name = "max_speed_mps", precision = 6, scale = 2)
    private BigDecimal maxSpeedMps;

    /**
     * Breakdown of activities by type.
     * Example: {"Run": 5, "Ride": 3, "Hike": 2}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "activity_type_breakdown", columnDefinition = "jsonb")
    private Map<String, Integer> activityTypeBreakdown;

    @Column(name = "personal_records_set")
    @Builder.Default
    private Integer personalRecordsSet = 0;

    @Column(name = "achievements_earned")
    @Builder.Default
    private Integer achievementsEarned = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Period type for the summary.
     */
    public enum PeriodType {
        WEEK,
        MONTH,
        YEAR
    }
}
