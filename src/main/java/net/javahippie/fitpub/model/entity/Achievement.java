package net.javahippie.fitpub.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing an achievement/badge earned by a user.
 */
@Entity
@Table(name = "achievements",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "achievement_type"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Achievement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "achievement_type", nullable = false, length = 100)
    @Enumerated(EnumType.STRING)
    private AchievementType achievementType;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "badge_icon", length = 50)
    private String badgeIcon; // Emoji or icon class

    @Column(name = "badge_color", length = 20)
    private String badgeColor;

    @Column(name = "earned_at", nullable = false)
    private LocalDateTime earnedAt;

    @Column(name = "activity_id")
    private UUID activityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Types of achievements that can be earned.
     */
    public enum AchievementType {
        // First time achievements
        FIRST_ACTIVITY,
        FIRST_RUN,
        FIRST_RIDE,
        FIRST_HIKE,

        // Distance milestones
        DISTANCE_10K,
        DISTANCE_50K,
        DISTANCE_100K,
        DISTANCE_500K,
        DISTANCE_1000K,

        // Activity count milestones
        ACTIVITIES_10,
        ACTIVITIES_50,
        ACTIVITIES_100,
        ACTIVITIES_500,
        ACTIVITIES_1000,

        // Streak achievements
        STREAK_7_DAYS,
        STREAK_30_DAYS,
        STREAK_100_DAYS,
        STREAK_365_DAYS,

        // Time-based achievements
        EARLY_BIRD,          // 5+ activities before 6am
        NIGHT_OWL,           // 5+ activities after 10pm
        WEEKEND_WARRIOR,     // 10+ weekend activities

        // Elevation achievements
        MOUNTAINEER_1000M,   // 1000m elevation gain in single activity
        MOUNTAINEER_5000M,   // 5000m total elevation gain
        MOUNTAINEER_10000M,  // 10000m total elevation gain

        // Consistency achievements
        CONSISTENT_WEEK,     // 7 days in a row
        CONSISTENT_MONTH,    // 30 days in a row

        // Exploration achievements
        EXPLORER,            // Activities in 5+ different locations
        GLOBE_TROTTER,       // Activities in 10+ different locations

        // Speed achievements
        SPEED_DEMON,         // Max speed > 50 km/h

        // Variety achievements
        VARIETY_SEEKER       // 3+ different activity types
    }
}
