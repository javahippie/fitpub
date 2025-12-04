package org.operaton.fitpub.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a personal record (PR) for a user.
 * Tracks best performances across different metrics and activity types.
 */
@Entity
@Table(name = "personal_records",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "activity_type", "record_type"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "activity_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ActivityType activityType;

    @Column(name = "record_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private RecordType recordType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "activity_id")
    private UUID activityId;

    @Column(name = "achieved_at", nullable = false)
    private LocalDateTime achievedAt;

    @Column(name = "previous_value", precision = 10, scale = 2)
    private BigDecimal previousValue;

    @Column(name = "previous_achieved_at")
    private LocalDateTime previousAchievedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Type of personal record being tracked.
     */
    public enum RecordType {
        FASTEST_1K,              // Fastest 1 kilometer time
        FASTEST_5K,              // Fastest 5 kilometer time
        FASTEST_10K,             // Fastest 10 kilometer time
        FASTEST_HALF_MARATHON,   // Fastest half marathon (21.1 km)
        FASTEST_MARATHON,        // Fastest marathon (42.2 km)
        LONGEST_DISTANCE,        // Longest distance in single activity
        LONGEST_DURATION,        // Longest duration in single activity
        HIGHEST_ELEVATION_GAIN,  // Highest elevation gain in single activity
        MAX_SPEED,               // Maximum speed achieved
        BEST_AVERAGE_PACE        // Best average pace for activity type
    }

    /**
     * Activity type for the personal record.
     */
    public enum ActivityType {
        RUN,
        RIDE,
        HIKE,
        WALK,
        SWIM,
        OTHER
    }
}
